package dev.aurora.comum;

import java.net.URI;

import org.jspecify.annotations.Nullable;

/**
 * Endereço para a API falar com o provedor de identidade (ADR 0022). O emissor dos tokens
 * é o endereço público (validado no claim {@code iss}); chaves e Admin API podem ser
 * buscadas por um endereço interno (ex.: {@code http://keycloak:8080}), sem sair para a
 * internet e voltar.
 */
public final class EnderecoDoEmissor {

	private EnderecoDoEmissor() {
	}

	/**
	 * {@code https://id.empresa/realms/x} com interno {@code http://keycloak:8080} →
	 * {@code http://keycloak:8080/realms/x}; sem endereço interno, o próprio emissor.
	 */
	public static String paraServidor(String emissor, @Nullable String enderecoInterno) {
		if (enderecoInterno == null || enderecoInterno.isBlank()) {
			return emissor;
		}
		var publico = URI.create(emissor);
		var interno = enderecoInterno.strip().replaceAll("/+$", "");
		return interno + publico.getRawPath();
	}

}
