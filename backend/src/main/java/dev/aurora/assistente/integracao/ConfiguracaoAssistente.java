package dev.aurora.assistente.integracao;

import java.time.Duration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.aurora.assistente.service.ModeloDoAssistente;
import dev.aurora.comum.PropriedadesIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provedor do assistente pela configuração {@code aurora.ia} (ADR 0019): Claude (padrão)
 * ou OpenAI. Configuração incompleta desliga o assistente, com aviso no log.
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoAssistente {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoAssistente.class);

	/**
	 * Pergunta é interativa: tempo curto e poucas tentativas (a pessoa está esperando).
	 */
	private static final Duration TEMPO_MAXIMO = Duration.ofSeconds(90);

	@Bean
	ModeloDoAssistente modeloDoAssistente(PropriedadesIa ia, JsonMapper json) {
		if (!ia.ligada()) {
			LOG.warn("Assistente desligado: configure {}.",
					ia.usaOpenAi() ? "AURORA_IA_OPENAI_CHAVE e AURORA_IA_OPENAI_MODELO" : "AURORA_IA_CHAVE");
			return new AssistenteDesligado();
		}
		if (ia.usaOpenAi()) {
			var openAi = ia.openAi();
			var cliente = OpenAIOkHttpClient.builder().apiKey(openAi.chave()).timeout(TEMPO_MAXIMO).maxRetries(2);
			if (PropriedadesIa.preenchido(openAi.urlBase())) {
				cliente.baseUrl(openAi.urlBase());
			}
			LOG.info("Assistente com a OpenAI ({})", openAi.modelo());
			return new AssistenteOpenAi(cliente.build(), openAi, json);
		}
		var cliente = AnthropicOkHttpClient.builder().apiKey(ia.chave()).timeout(TEMPO_MAXIMO).maxRetries(2);
		if (PropriedadesIa.preenchido(ia.urlBase())) {
			cliente.baseUrl(ia.urlBase());
		}
		return new AssistenteClaude(cliente.build(), ia.modelo());
	}

}
