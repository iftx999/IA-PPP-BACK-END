package dev.aurora.contratos.integracao;

import java.util.ArrayList;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonSchemaLocalValidation;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.aurora.comum.PropriedadesIa;
import dev.aurora.contratos.model.Proposta;
import dev.aurora.contratos.model.ResultadoExtracao;
import dev.aurora.contratos.model.Trecho;
import dev.aurora.contratos.service.ExtratorContrato;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extração com a OpenAI (ADR 0019): mesmas instruções, lotes e esquema
 * ({@link SaidaExtracao}) do provedor Claude, com saída estruturada estrita. A validação
 * do trecho literal e a revisão humana seguem iguais (ADR 0014).
 */
class ExtratorOpenAi implements ExtratorContrato {

	private static final Logger LOG = LoggerFactory.getLogger(ExtratorOpenAi.class);

	private final OpenAIClient cliente;

	private final PropriedadesIa.OpenAi configuracao;

	ExtratorOpenAi(OpenAIClient cliente, PropriedadesIa.OpenAi configuracao) {
		this.cliente = cliente;
		this.configuracao = configuracao;
	}

	@Override
	public boolean disponivel() {
		return true;
	}

	@Override
	public ResultadoExtracao extrair(List<Trecho> trechos, List<String> unidades) throws ExtracaoFalhouException {
		var propostas = new ArrayList<Proposta>();
		var observacoes = new ArrayList<String>();
		var lotes = ExtratorClaude.lotes(trechos);
		String modelo = configuracao.modelo();
		for (int i = 0; i < lotes.size(); i++) {
			var saida = chamar(ExtratorClaude.mensagem(lotes.get(i), unidades), i + 1, lotes.size(), observacoes);
			for (var item : saida.itens()) {
				propostas.add(ExtratorClaude.proposta(item));
			}
			saida.observacoes().stream().filter(o -> !o.isBlank()).limit(3).forEach(observacoes::add);
		}
		return new ResultadoExtracao(propostas, modelo, ExtratorClaude.VERSAO_PROMPT, observacoes);
	}

	private SaidaExtracao chamar(String mensagem, int lote, int total, List<String> observacoes)
			throws ExtracaoFalhouException {
		var parametros = ChatCompletionCreateParams.builder()
			.model(configuracao.modelo())
			.maxCompletionTokens(16_000L)
			.responseFormat(SaidaExtracao.class, JsonSchemaLocalValidation.YES)
			.addDeveloperMessage(ExtratorClaude.INSTRUCOES)
			.addUserMessage(mensagem);
		if (PropriedadesIa.preenchido(configuracao.esforco())) {
			parametros.reasoningEffort(ReasoningEffort.of(configuracao.esforco().strip().toLowerCase()));
		}
		try {
			var resposta = cliente.chat().completions().create(parametros.build());
			var escolha = resposta.choices().getFirst();
			if (escolha.message().refusal().isPresent() || FinishReason.CONTENT_FILTER.equals(escolha.finishReason())) {
				throw new ExtracaoFalhouException("A IA recusou analisar o lote " + lote + " de " + total + ".");
			}
			if (FinishReason.LENGTH.equals(escolha.finishReason())) {
				observacoes.add("O lote " + lote + " de " + total + " tinha itens demais e pode estar incompleto.");
			}
			resposta.usage()
				.ifPresent(u -> LOG.info("Extração (OpenAI) lote {}/{}: {} tokens de entrada, {} de saída", lote, total,
						u.promptTokens(), u.completionTokens()));
			return escolha.message()
				.content()
				.orElseThrow(
						() -> new ExtracaoFalhouException("A IA não devolveu resultado para o lote " + lote + "."));
		}
		catch (UnauthorizedException erro) {
			throw new ExtracaoFalhouException("A chave da API de IA foi recusada. Verifique a configuração.", erro);
		}
		catch (RateLimitException erro) {
			throw new ExtracaoFalhouException("O limite de uso da IA foi atingido. Tente reprocessar mais tarde.",
					erro);
		}
		catch (OpenAIServiceException erro) {
			LOG.error("API da OpenAI respondeu {}", erro.statusCode());
			throw new ExtracaoFalhouException("O serviço de IA falhou ao analisar o contrato. Tente reprocessar.",
					erro);
		}
		catch (OpenAIIoException erro) {
			throw new ExtracaoFalhouException("Não foi possível falar com o serviço de IA. Tente reprocessar.", erro);
		}
	}

}
