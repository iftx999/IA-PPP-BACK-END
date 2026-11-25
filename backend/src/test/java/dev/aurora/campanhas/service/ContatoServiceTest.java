package dev.aurora.campanhas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContatoServiceTest {

	@Test
	void celularBrasileiroEmE164() {
		assertThat(ContatoService.celular("(19) 99876-5432")).contains("+5519998765432");
		assertThat(ContatoService.celular("+55 19 99876 5432")).contains("+5519998765432");
		assertThat(ContatoService.celular("019998765432")).isEmpty();
		assertThat(ContatoService.celular("1933334444")).isEmpty();
		assertThat(ContatoService.celular("19 3333 44445")).isEmpty();
		assertThat(ContatoService.celular("abc")).isEmpty();
	}

	@Test
	void cifraIdaEVoltaEAdulteracaoRecusada() throws Exception {
		var cifra = new CifraDeContatos("uma-chave-de-teste-com-mais-de-32-caracteres");
		var cifrado = cifra.cifrar("+5519998765432");
		assertThat(cifrado).doesNotContain("99876");
		assertThat(cifra.cifrar("+5519998765432")).isNotEqualTo(cifrado);
		assertThat(cifra.decifrar(cifrado)).isEqualTo("+5519998765432");
		var adulterado = cifrado.substring(0, cifrado.length() - 4) + "AAAA";
		assertThatThrownBy(() -> cifra.decifrar(adulterado)).isInstanceOf(IllegalStateException.class);
		var outra = new CifraDeContatos("outra-chave-de-teste-com-mais-de-32-caracteres");
		assertThatThrownBy(() -> outra.decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CifraDeContatos("curta")).isInstanceOf(IllegalArgumentException.class);
	}

}
