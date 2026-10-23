package dev.aurora.assistente.integracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.Papel;
import dev.aurora.assistente.model.RespostaDoModelo;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.model.TurnoAnterior;
import dev.aurora.assistente.model.Uso;
import dev.aurora.assistente.service.AssistenteIndisponivelException;
import dev.aurora.assistente.service.ModeloDoAssistente;
import dev.aurora.comum.PropriedadesIa;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assistente com a OpenAI (ADR 0019): mesmas instruções, ferramentas estritas e laço do
 * provedor Claude, pela API de Chat Completions. Recusa ({@code refusal}) ou filtro de
 * conteúdo viram a resposta padrão de recusa.
 */
class AssistenteOpenAi implements ModeloDoAssistente {

	private static final Logger LOG = LoggerFactory.getLogger(AssistenteOpenAi.class);

	static final int MAXIMO_DE_RODADAS = 6;

	private static final Pattern MARCA = Pattern.compile("\\s*\\[\\d+]");

	private static final BigDecimal MILHAO = BigDecimal.valueOf(1_000_000);

	private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
	};

	private final OpenAIClient cliente;

	private final PropriedadesIa.OpenAi configuracao;

	private final JsonMapper json;

	AssistenteOpenAi(OpenAIClient cliente, PropriedadesIa.OpenAi configuracao, JsonMapper json) {
		this.cliente = cliente;
		this.configuracao = configuracao;
		this.json = json;
	}

	@Override
	public boolean disponivel() {
		return true;
	}

	@Override
	public RespostaDoModelo responder(List<TurnoAnterior> historico, String pergunta,
			List<DefinicaoFerramenta> ferramentas, BiFunction<String, Map<String, Object>, SaidaFerramenta> executor) {
		var parametros = ChatCompletionCreateParams.builder()
			.model(configuracao.modelo())
			.maxCompletionTokens(16_000L)
			.addDeveloperMessage(InstrucoesDoAssistente.TEXTO);
		if (PropriedadesIa.preenchido(configuracao.esforco())) {
			parametros.reasoningEffort(ReasoningEffort.of(configuracao.esforco().strip().toLowerCase()));
		}
		ferramentas.forEach(f -> parametros.addTool(ferramenta(f)));
		for (var turno : historico) {
			if (turno.papel() == Papel.USUARIO) {
				parametros.addUserMessage(turno.texto());
			}
			else {
				parametros.addAssistantMessage(MARCA.matcher(turno.texto()).replaceAll(""));
			}
		}
		parametros.addUserMessage(pergunta);

		var uso = Uso.ZERO;
		for (int rodada = 1; rodada <= MAXIMO_DE_RODADAS; rodada++) {
			var resposta = chamar(parametros.build());
			uso = uso.mais(uso(resposta));
			var escolha = resposta.choices().getFirst();
			var mensagem = escolha.message();
			if (mensagem.refusal().isPresent() || FinishReason.CONTENT_FILTER.equals(escolha.finishReason())) {
				LOG.info("Assistente (OpenAI) recusou a pergunta");
				return new RespostaDoModelo("", resposta.model(), uso, true, custo(uso));
			}
			if (FinishReason.LENGTH.equals(escolha.finishReason())) {
				throw new AssistenteIndisponivelException(
						"A resposta ficou longa demais. Faça uma pergunta mais específica.");
			}
			var chamadas = mensagem.toolCalls().orElse(List.of());
			if (chamadas.isEmpty()) {
				LOG.info(
						"Assistente (OpenAI) respondeu em {} rodada(s): {} tokens de entrada ({} do cache), {} de saída",
						rodada, uso.entrada(), uso.cacheLido(), uso.saida());
				return new RespostaDoModelo(mensagem.content().orElse("").strip(), resposta.model(), uso, false,
						custo(uso));
			}
			parametros.addMessage(mensagem);
			for (var chamada : chamadas) {
				var funcao = chamada.asFunction();
				var saida = executor.apply(funcao.function().name(), entrada(funcao.function().arguments()));
				parametros.addMessage(ChatCompletionToolMessageParam.builder()
					.toolCallId(funcao.id())
					.content(saida.erro() ? "{\"erro_da_ferramenta\":true,\"detalhe\":" + saida.json() + "}"
							: saida.json())
					.build());
			}
		}
		throw new AssistenteIndisponivelException(
				"O assistente não chegou a uma resposta com os dados disponíveis. Reformule a pergunta.");
	}

	private ChatCompletion chamar(ChatCompletionCreateParams parametros) {
		try {
			return cliente.chat().completions().create(parametros);
		}
		catch (UnauthorizedException erro) {
			throw new AssistenteIndisponivelException("A chave da API de IA foi recusada. Avise o administrador.",
					erro);
		}
		catch (RateLimitException erro) {
			throw new AssistenteIndisponivelException(
					"O serviço de IA está no limite de uso agora. Tente de novo em instantes.", erro);
		}
		catch (OpenAIServiceException erro) {
			LOG.error("API da OpenAI respondeu {}", erro.statusCode());
			throw new AssistenteIndisponivelException("O serviço de IA falhou. Tente de novo em instantes.", erro);
		}
		catch (OpenAIIoException erro) {
			throw new AssistenteIndisponivelException("Não foi possível falar com o serviço de IA.", erro);
		}
	}

	/**
	 * Argumentos chegam como texto JSON; inválido vira entrada vazia (a ferramenta
	 * recusa).
	 */
	private Map<String, Object> entrada(String argumentos) {
		try {
			return json.readValue(argumentos, MAPA);
		}
		catch (RuntimeException erro) {
			return Map.of();
		}
	}

	static ChatCompletionFunctionTool ferramenta(DefinicaoFerramenta definicao) {
		var esquema = FunctionParameters.builder()
			.putAdditionalProperty("type", JsonValue.from("object"))
			.putAdditionalProperty("properties", JsonValue.from(definicao.propriedades()))
			.putAdditionalProperty("required", JsonValue.from(definicao.obrigatorias()))
			.putAdditionalProperty("additionalProperties", JsonValue.from(false))
			.build();
		return ChatCompletionFunctionTool.builder()
			.function(FunctionDefinition.builder()
				.name(definicao.nome())
				.description(definicao.descricao())
				.parameters(esquema)
				.strict(true)
				.build())
			.build();
	}

	/** Na OpenAI, tokens do cache estão contidos nos de entrada. */
	private static Uso uso(ChatCompletion resposta) {
		return resposta.usage().map(u -> {
			long cache = u.promptTokensDetails().flatMap(d -> d.cachedTokens()).orElse(0L);
			return new Uso(u.promptTokens() - cache, u.completionTokens(), cache, 0);
		}).orElse(Uso.ZERO);
	}

	private @Nullable BigDecimal custo(Uso uso) {
		if (configuracao.precoEntrada() == null || configuracao.precoSaida() == null) {
			return null;
		}
		var cache = configuracao.precoCacheLido() == null ? configuracao.precoEntrada() : configuracao.precoCacheLido();
		return configuracao.precoEntrada()
			.multiply(BigDecimal.valueOf(uso.entrada()))
			.add(configuracao.precoSaida().multiply(BigDecimal.valueOf(uso.saida())))
			.add(cache.multiply(BigDecimal.valueOf(uso.cacheLido())))
			.divide(MILHAO, 6, RoundingMode.HALF_UP);
	}

}
