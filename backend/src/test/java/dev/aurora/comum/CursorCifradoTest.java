package dev.aurora.comum;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CursorCifradoTest {

	private final CursorCifrado cursor;

	CursorCifradoTest() throws Exception {
		cursor = new CursorCifrado("uma-chave-de-teste-com-mais-de-32-caracteres");
	}

	@Test
	void idaEVoltaSemExporOValor() {
		var cifrado = cursor.cifrar("2001763");
		assertThat(cifrado).doesNotContain("2001763");
		assertThat(cursor.decifrar(cifrado)).contains("2001763");
	}

	@Test
	void cadaCifragemEhDiferente() {
		assertThat(cursor.cifrar("2001763")).isNotEqualTo(cursor.cifrar("2001763"));
	}

	@Test
	void cursorAdulteradoOuInvalidoEhIgnorado() {
		var cifrado = cursor.cifrar("2001763");
		var adulterado = cifrado.substring(0, cifrado.length() - 2) + (cifrado.endsWith("A") ? "BB" : "AA");
		assertThat(cursor.decifrar(adulterado)).isEmpty();
		assertThat(cursor.decifrar("lixo")).isEmpty();
		assertThat(cursor.decifrar("")).isEmpty();
	}

}
