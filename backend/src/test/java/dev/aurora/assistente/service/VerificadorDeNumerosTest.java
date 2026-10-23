package dev.aurora.assistente.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificadorDeNumerosTest {

	private static final List<String> SAIDAS = List.of("""
			{"competencia":"2026-08","unidades":[{"fonte":1,"adesaoPercentual":85.41,"ligados":4954,
			 "imoveisComRede":5800,"variacaoPontosPercentuais":-0.30,"metaPercentual":85.00,"cortados":120}]}""");

	@Test
	void aceitaEscritaBrasileiraEArredondamento() {
		assertThat(VerificadorDeNumeros
			.semFonte("A Cidade A está com 85,41% [1] (85,4%), 4.954 ligados de 5.800 imóveis em ago/2026, variação de "
					+ "-0,30 p.p. e meta de 85% [1]. São 3 setores.", SAIDAS))
			.isEmpty();
	}

	@Test
	void apontaNumerosQueNaoVieramDasFerramentas() {
		assertThat(
				VerificadorDeNumeros.semFonte("A adesão deve chegar a 90,5% em 2027, faltando 655 ligações.", SAIDAS))
			.containsExactly("90,5", "2027", "655");
	}

	@Test
	void interpretaSeparadores() {
		assertThat(VerificadorDeNumeros.interpretar("1.234.567")).isEqualByComparingTo("1234567");
		assertThat(VerificadorDeNumeros.interpretar("4.954,5")).isEqualByComparingTo(new BigDecimal("4954.5"));
		assertThat(VerificadorDeNumeros.interpretar("85.41")).isEqualByComparingTo("85.41");
	}

}
