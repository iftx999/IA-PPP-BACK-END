package dev.aurora.comum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Nomes repetidos de DTOs colidiriam na OpenAPI (um esquema sobrescreveria o outro). */
class NomesDosRecordsDaApiTest {

	@Test
	void naoHaDtosComOMesmoNome() {
		assertThatCode(ConfiguracaoOpenApi::recordsDaAplicacao).doesNotThrowAnyException();
	}

}
