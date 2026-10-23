package dev.aurora.comum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnderecoDoEmissorTest {

	@Test
	void trocaSoAOrigemPeloEnderecoInterno() {
		assertThat(EnderecoDoEmissor.paraServidor("https://id.empresa.com.br/realms/aurora", "http://keycloak:8080/"))
			.isEqualTo("http://keycloak:8080/realms/aurora");
		assertThat(EnderecoDoEmissor.paraServidor("https://id.empresa.com.br/realms/aurora", " "))
			.isEqualTo("https://id.empresa.com.br/realms/aurora");
		assertThat(EnderecoDoEmissor.paraServidor("https://id.empresa.com.br/realms/aurora", null))
			.isEqualTo("https://id.empresa.com.br/realms/aurora");
	}

}
