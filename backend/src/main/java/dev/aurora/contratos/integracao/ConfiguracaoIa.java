package dev.aurora.contratos.integracao;

import java.time.Duration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import dev.aurora.contratos.service.ExtratorContrato;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoIa.Propriedades.class)
class ConfiguracaoIa {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoIa.class);

	/**
	 * @param chave chave da API (cofre de segredos); vazia desliga a extração
	 * @param modelo modelo do Claude usado na extração
	 * @param urlBase endereço da API (vazio = padrão; outro valor só para testes ou
	 * proxy)
	 */
	@ConfigurationProperties("aurora.ia")
	record Propriedades(String chave, String modelo, String urlBase) {
	}

	@Bean
	ExtratorContrato extratorContrato(Propriedades propriedades) {
		if (propriedades.chave() == null || propriedades.chave().isBlank()) {
			LOG.warn("Extração de contratos por IA desligada: defina AURORA_IA_CHAVE para ligar.");
			return new ExtratorDesligado();
		}
		var cliente = AnthropicOkHttpClient.builder()
			.apiKey(propriedades.chave())
			.timeout(Duration.ofMinutes(5))
			.maxRetries(3);
		if (propriedades.urlBase() != null && !propriedades.urlBase().isBlank()) {
			cliente.baseUrl(propriedades.urlBase());
		}
		return new ExtratorClaude(cliente.build(), propriedades.modelo());
	}

}
