package dev.aurora.acesso.integracao;

import java.net.http.HttpClient;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoIdentidade.Propriedades.class)
class ConfiguracaoIdentidade {

	/**
	 * @param clienteId client com conta de serviço (papéis manage-users e view-users) em
	 * cada realm de tenant
	 * @param segredo segredo desse client; vem do cofre de segredos
	 * @param clienteWeb client do front, para onde o link do e-mail leva depois da ação
	 * @param urlRetorno endereço do front depois de concluir o convite
	 * @param validadeConvite prazo dos links de convite e de redefinição de senha
	 */
	@ConfigurationProperties("aurora.identidade")
	/**
	 * @param urlInterna endereço do provedor na rede interna (ex.: http://keycloak:8080);
	 * vazio = o público
	 */
	record Propriedades(String clienteId, String segredo, String clienteWeb, String urlRetorno,
			Duration validadeConvite, @Nullable String urlInterna) {
	}

	@Bean
	KeycloakProvedorIdentidade provedorIdentidade(Propriedades propriedades) {
		var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		var fabrica = new JdkClientHttpRequestFactory(http);
		fabrica.setReadTimeout(Duration.ofSeconds(10));
		return new KeycloakProvedorIdentidade(RestClient.builder().requestFactory(fabrica), propriedades);
	}

}
