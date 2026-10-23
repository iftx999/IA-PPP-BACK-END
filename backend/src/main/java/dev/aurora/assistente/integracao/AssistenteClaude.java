package dev.aurora.assistente.integracao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.Papel;
import dev.aurora.assistente.model.RespostaDoModelo;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.model.TurnoAnterior;
import dev.aurora.assistente.model.Uso;
import dev.aurora.assistente.service.AssistenteIndisponivelException;
import dev.aurora.assistente.service.ModeloDoAssistente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Laço manual de ferramentas com a API do Claude (doc 05, 5.4). Instruções e ferramentas
 * são fixas (prefixo em cache); a data e a unidade de contexto vão na mensagem. A
 * resposta inteira volta ao histórico a cada rodada (blocos de raciocínio preservados), e
 * todos os resultados de uma rodada seguem numa única mensagem.
 */
class AssistenteClaude implements ModeloDoAssistente {

	private static final Logger LOG = LoggerFactory.getLogger(AssistenteClaude.class);

	/** Rodadas de ferramenta por pergunta antes de desistir. */
	static final int MAXIMO_DE_RODADAS = 6;

	private static final Pattern MARCA = Pattern.compile("\\s*\\[\\d+]");

	private final AnthropicClient cliente;

	private final String modelo;

	AssistenteClaude(AnthropicClient cliente, String modelo) {
		this.cliente = cliente;
		this.modelo = modelo;
	}

	@Override
	public boolean disponivel() {
		return true;
	}

	@Override
	public RespostaDoModelo responder(List<TurnoAnterior> historico, String pergunta,
			List<DefinicaoFerramenta> ferramentas, BiFunction<String, Map<String, Object>, SaidaFerramenta> executor) {
		var parametros = MessageCreateParams.builder()
			.model(modelo)
			.maxTokens(16_000L)
			.systemOfTextBlockParams(List.of(TextBlockParam.builder()
				.text(InstrucoesDoAssistente.TEXTO)
				.cacheControl(CacheControlEphemeral.builder().build())
				.build()))
			// Perguntas de consulta: esforço médio basta e responde mais rápido.
			.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
			// Recusa por classificador de segurança: o servidor tenta outro modelo.
			.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
			.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
		ferramentas.forEach(f -> parametros.addTool(ferramenta(f)));
		for (var turno : historico) {
			// Marcas [n] de respostas antigas apontam para fontes de outra pergunta.
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
			var parada = resposta.stopReason().orElse(null);
			if (StopReason.REFUSAL.equals(parada)) {
				LOG.info("Assistente recusou ({})",
						resposta.stopDetails()
							.flatMap(d -> d.category())
							.map(Object::toString)
							.orElse("sem categoria"));
				return new RespostaDoModelo("", resposta.model().asString(), uso, true,
						CustoDoUso.calcular(resposta.model().asString(), uso).orElse(null));
			}
			if (StopReason.MAX_TOKENS.equals(parada)) {
				throw new AssistenteIndisponivelException(
						"A resposta ficou longa demais. Faça uma pergunta mais específica.");
			}
			if (!StopReason.TOOL_USE.equals(parada)) {
				LOG.info("Assistente respondeu em {} rodada(s): {} tokens de entrada ({} do cache), {} de saída",
						rodada, uso.entrada(), uso.cacheLido(), uso.saida());
				return new RespostaDoModelo(texto(resposta), resposta.model().asString(), uso, false,
						CustoDoUso.calcular(resposta.model().asString(), uso).orElse(null));
			}
			parametros.addMessage(resposta);
			parametros.addMessage(resultados(resposta, executor));
		}
		throw new AssistenteIndisponivelException(
				"O assistente não chegou a uma resposta com os dados disponíveis. Reformule a pergunta.");
	}

	private Message chamar(MessageCreateParams parametros) {
		try {
			return cliente.messages().create(parametros);
		}
		catch (UnauthorizedException erro) {
			throw new AssistenteIndisponivelException("A chave da API de IA foi recusada. Avise o administrador.",
					erro);
		}
		catch (RateLimitException erro) {
			throw new AssistenteIndisponivelException(
					"O serviço de IA está no limite de uso agora. Tente de novo em instantes.", erro);
		}
		catch (AnthropicServiceException erro) {
			LOG.error("API do Claude respondeu {} ({})", erro.statusCode(), erro.errorType().orElse(null));
			throw new AssistenteIndisponivelException("O serviço de IA falhou. Tente de novo em instantes.", erro);
		}
		catch (AnthropicIoException erro) {
			throw new AssistenteIndisponivelException("Não foi possível falar com o serviço de IA.", erro);
		}
	}

	/** Executa todos os pedidos de ferramenta da rodada; os resultados vão juntos. */
	@SuppressWarnings("unchecked")
	private static MessageParam resultados(Message resposta,
			BiFunction<String, Map<String, Object>, SaidaFerramenta> executor) {
		var blocos = new ArrayList<ContentBlockParam>();
		resposta.content().stream().flatMap(b -> b.toolUse().stream()).forEach(pedido -> {
			var entrada = pedido._input().convert(Map.class);
			var saida = executor.apply(pedido.name(), entrada == null ? Map.of() : (Map<String, Object>) entrada);
			blocos.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
				.toolUseId(pedido.id())
				.content(saida.json())
				.isError(saida.erro())
				.build()));
		});
		return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocos).build();
	}

	static Tool ferramenta(DefinicaoFerramenta definicao) {
		var propriedades = Tool.InputSchema.Properties.builder();
		definicao.propriedades()
			.forEach((nome, esquema) -> propriedades.putAdditionalProperty(nome, JsonValue.from(esquema)));
		return Tool.builder()
			.name(definicao.nome())
			.description(definicao.descricao())
			.inputSchema(Tool.InputSchema.builder()
				.properties(propriedades.build())
				.required(definicao.obrigatorias())
				.putAdditionalProperty("additionalProperties", JsonValue.from(false))
				.build())
			.strict(true)
			.build();
	}

	private static String texto(Message resposta) {
		var texto = new StringBuilder();
		resposta.content().stream().flatMap(b -> b.text().stream()).forEach(t -> {
			if (!texto.isEmpty()) {
				texto.append("\n\n");
			}
			texto.append(t.text());
		});
		return texto.toString().strip();
	}

	private static Uso uso(Message resposta) {
		var u = resposta.usage();
		return new Uso(u.inputTokens(), u.outputTokens(), u.cacheReadInputTokens().orElse(0L),
				u.cacheCreationInputTokens().orElse(0L));
	}

}
