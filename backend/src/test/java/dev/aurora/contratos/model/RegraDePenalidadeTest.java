package dev.aurora.contratos.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0035: multa de uma aferição pelas fórmulas, contagem de pontos e teto. */
class RegraDePenalidadeTest {

	@ParameterizedTest(name = "{0} {1} base={2} {3} teto={4}, {5} pp → R$ {6}")
	@CsvSource(nullValues = "-", textBlock = """
			PERCENTUAL_DA_BASE_POR_PONTO, 0.1,   10000000, PROPORCIONAL,        -,     2.4,    24000.00
			PERCENTUAL_DA_BASE_POR_PONTO, 0.1,   10000000, POR_PONTO_OU_FRACAO, -,     2.4,    30000.00
			PERCENTUAL_DA_BASE_POR_PONTO, 0.1,   10000000, POR_PONTO_OU_FRACAO, -,     2.0,    20000.00
			PERCENTUAL_DA_BASE_POR_PONTO, 0.1,   10000000, POR_PONTO_OU_FRACAO, 25000, 2.4,    25000.00
			VALOR_POR_PONTO,              1500,  -,        PROPORCIONAL,        -,     0.333,  499.50
			VALOR_POR_PONTO,              1500,  -,        POR_PONTO_OU_FRACAO, -,     0.0001, 1500.00
			VALOR_FIXO,                   80000, -,        PROPORCIONAL,        -,     0.01,   80000.00
			VALOR_FIXO,                   80000, -,        PROPORCIONAL,        -,     0,      0.00
			VALOR_POR_PONTO,              1500,  -,        PROPORCIONAL,        -,     -1.5,   0.00
			""")
	void multa(FormulaPenalidade formula, BigDecimal valor, BigDecimal base, ContagemPontos contagem, BigDecimal teto,
			BigDecimal pontos, BigDecimal esperada) {
		var regra = new RegraDePenalidade(UUID.randomUUID(), UUID.randomUUID(), "001", "Multa", null, 1,
				UUID.randomUUID(), "Concessão", formula, valor, base, null, contagem, PeriodicidadePenalidade.MENSAL,
				teto, Instant.now(), null);
		assertThat(regra.multa(pontos)).isEqualByComparingTo(esperada).hasScaleOf(2);
	}

}
