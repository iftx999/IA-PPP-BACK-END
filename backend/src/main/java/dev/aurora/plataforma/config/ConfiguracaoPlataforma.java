package dev.aurora.plataforma.config;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoPlataforma.Propriedades.class)
public class ConfiguracaoPlataforma {

	/**
	 * Administração da plataforma (ADR 0027).
	 *
	 * @param emissor realm de quem administra a plataforma (só ele entra em
	 * /api/v1/plataforma)
	 * @param segundoFator exige {@code otp} no {@code amr} (desligado só no
	 * desenvolvimento)
	 * @param urlIdentidade endereço público do Keycloak; o emissor de cada cliente novo é
	 * {@code <urlIdentidade>/realms/<código>}
	 * @param clienteId conta de serviço no realm master com o papel create-realm
	 * @param segredo segredo dessa conta (cofre de segredos)
	 * @param tempoLimite prazo das chamadas ao Keycloak (criar realm leva alguns
	 * segundos)
	 */
	@ConfigurationProperties("aurora.plataforma")
	public record Propriedades(String emissor, boolean segundoFator, String urlIdentidade, String clienteId,
			@Nullable String segredo, Duration tempoLimite) {
	}

}
