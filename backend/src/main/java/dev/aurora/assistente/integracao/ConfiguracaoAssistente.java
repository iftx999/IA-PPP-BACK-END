package dev.aurora.assistente.integracao;

import java.time.Duration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.aurora.assistente.service.ModeloDoAssistente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mesma chave e modelo da extração de contratos ({@code aurora.ia}, ADR 0014). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoAssistente.Propriedades.class)
class ConfiguracaoAssistente {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoAssistente.class);

	@ConfigurationProperties("aurora.ia")
	record Propriedades(String chave, String modelo, String urlBase) {
	}

	@Bean
	ModeloDoAssistente modeloDoAssistente(Propriedades propriedades) {
		if (propriedades.chave() == null || propriedades.chave().isBlank()) {
			LOG.warn("Assistente desligado: defina AURORA_IA_CHAVE para ligar.");
			return new AssistenteDesligado();
		}
		// Pergunta é interativa: tempo curto e poucas tentativas (a pessoa está
		// esperando).
		var cliente = AnthropicOkHttpClient.builder()
			.apiKey(propriedades.chave())
			.timeout(Duration.ofSeconds(90))
			.maxRetries(2);
		if (propriedades.urlBase() != null && !propriedades.urlBase().isBlank()) {
			cliente.baseUrl(propriedades.urlBase());
		}
		return new AssistenteClaude(cliente.build(), propriedades.modelo());
	}

}
