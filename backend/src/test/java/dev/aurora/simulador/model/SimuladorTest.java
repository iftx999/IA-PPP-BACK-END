package dev.aurora.simulador.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.adesao.BaseDaSimulacao.MesMedido;
import dev.aurora.adesao.BaseDaSimulacao.MetaDaSimulacao;
import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0035: projeção mês a mês e multa estimada pelas regras calculáveis. */
class SimuladorTest {

	private static final YearMonth AGO = YearMonth.of(2026, 8);

	private static final YearMonth DEZ = YearMonth.of(2026, 12);

	/** Concessão do demo: 8.761 de 11.400 (76,85%), meta 80% até dez/2026. */
	private static BaseDaSimulacao base(YearMonth prazo) {
		return new BaseDaSimulacao(UUID.randomUUID(), "Concessão", "CONCESSAO", AGO,
				List.of(new MesMedido(AGO.minusMonths(1), 8_680, 11_400), new MesMedido(AGO, 8_761, 11_400)),
				new BigDecimal("81.0"),
				new MetaDaSimulacao(new BigDecimal("80"), prazo, UUID.randomUUID(), "Concessão", false, "Contrato"));
	}

	private static RegraDePenalidade regra(FormulaPenalidade formula, String valor, String base,
			ContagemPontos contagem, PeriodicidadePenalidade periodicidade, String teto) {
		return new RegraDePenalidade(UUID.randomUUID(), UUID.randomUUID(), "001/2025",
				"Multa por adesão abaixo da meta", "39.5", 12, UUID.randomUUID(), "Concessão", formula,
				new BigDecimal(valor), base == null ? null : new BigDecimal(base), "Faturamento mensal", contagem,
				periodicidade, teto == null ? null : new BigDecimal(teto), Instant.now(), null);
	}

	private static RegraDePenalidade umDecimoDoFaturamento() {
		return regra(FormulaPenalidade.PERCENTUAL_DA_BASE_POR_PONTO, "0.1", "10000000",
				ContagemPontos.POR_PONTO_OU_FRACAO, PeriodicidadePenalidade.MENSAL, null);
	}

	@Test
	void ritmoAtualFicaAbaixoNoPrazoEAtingeNoMesSeguinte() {
		var cenario = Simulador.projetar(base(DEZ), new BigDecimal("81"), DEZ.plusMonths(3),
				List.of(umDecimoDoFaturamento()));

		// dez: 8.761 + 81 × 4 = 9.085 → 79,6930%: 0,307 pp abaixo, conta 1 ponto.
		var dezembro = cenario.meses().get(4);
		assertThat(dezembro.competencia()).isEqualTo(DEZ);
		assertThat(dezembro.ligados()).isEqualTo(9_085);
		assertThat(dezembro.adesao()).isEqualByComparingTo("79.6930");
		assertThat(dezembro.pontosAbaixo()).isEqualByComparingTo("0.3070");
		assertThat(dezembro.aferido()).isTrue();
		assertThat(dezembro.multa()).isEqualByComparingTo("10000.00");
		assertThat(cenario.adesaoNoPrazo()).isEqualByComparingTo("79.6930");

		// jan: 9.166 → 80,40%: atingida, a multa para.
		assertThat(cenario.mesAtingimento()).isEqualTo(DEZ.plusMonths(1));
		assertThat(cenario.multaTotal()).isEqualByComparingTo("10000.00");
		assertThat(cenario.multasPorRegra().getFirst().afericoesComMulta()).isEqualTo(1);

		// Antes do prazo não há aferição; o mês 0 é o medido.
		assertThat(cenario.meses().getFirst().medido()).isTrue();
		assertThat(cenario.meses().subList(0, 4)).noneMatch(Cenario.MesProjetado::aferido);
	}

	@Test
	void ligacoesNecessariasZeramAMulta() {
		// faltam ⌈80% × 11.400⌉ − 8.761 = 359 em 4 meses → 90/mês.
		var necessarias = Simulador.ligacoesNecessariasPorMes(base(DEZ));
		assertThat(necessarias).isEqualByComparingTo("90");
		var cenario = Simulador.projetar(base(DEZ), necessarias, DEZ.plusMonths(12), List.of(umDecimoDoFaturamento()));
		assertThat(cenario.adesaoNoPrazo()).isGreaterThanOrEqualTo(new BigDecimal("80"));
		assertThat(cenario.multaTotal()).isZero();
		assertThat(Simulador.projetar(base(DEZ), necessarias.subtract(BigDecimal.ONE), DEZ, List.of()).adesaoNoPrazo())
			.isLessThan(new BigDecimal("80"));
	}

	@Test
	void semLigacoesAMultaSeRepeteTodoMesAteOFimDoHorizonte() {
		var cenario = Simulador.projetar(base(DEZ), BigDecimal.ZERO, DEZ.plusMonths(11),
				List.of(umDecimoDoFaturamento()));
		// 76,85% o ano todo: 3,15 pp → 4 pontos (fração conta) → R$ 40 mil × 12
		// aferições.
		assertThat(cenario.meses().getLast().multa()).isEqualByComparingTo("40000.00");
		assertThat(cenario.multaTotal()).isEqualByComparingTo("480000.00");
		assertThat(cenario.mesAtingimento()).isNull();
	}

	@Test
	void periodicidadeAnualAfereNoPrazoEDozeMesesDepois() {
		var anual = regra(FormulaPenalidade.VALOR_FIXO, "50000", null, ContagemPontos.PROPORCIONAL,
				PeriodicidadePenalidade.ANUAL, null);
		var cenario = Simulador.projetar(base(DEZ), BigDecimal.ZERO, DEZ.plusMonths(24), List.of(anual));
		assertThat(
				cenario.meses().stream().filter(Cenario.MesProjetado::aferido).map(Cenario.MesProjetado::competencia))
			.containsExactly(DEZ, DEZ.plusMonths(12), DEZ.plusMonths(24));
		assertThat(cenario.multaTotal()).isEqualByComparingTo("150000.00");
	}

	@Test
	void prazoJaVencidoAfereDesdeOMesMedido() {
		var vencido = base(AGO.minusMonths(2));
		var cenario = Simulador.projetar(vencido, BigDecimal.ZERO, AGO.plusMonths(1),
				List.of(regra(FormulaPenalidade.VALOR_POR_PONTO, "1000", null, ContagemPontos.PROPORCIONAL,
						PeriodicidadePenalidade.MENSAL, null)));
		// 80 − 76,8509 = 3,1491 pp × R$ 1.000, em ago e set.
		assertThat(cenario.meses().getFirst().multa()).isEqualByComparingTo("3149.10");
		assertThat(cenario.multaTotal()).isEqualByComparingTo("6298.20");
		assertThat(cenario.adesaoNoPrazo()).isNull();
		assertThat(Simulador.ligacoesNecessariasPorMes(vencido)).isNull();
	}

	@Test
	void ligadosNuncaPassamDoTotalComRede() {
		var cenario = Simulador.projetar(base(DEZ), new BigDecimal("5000"), DEZ, List.of());
		assertThat(cenario.meses().getLast().ligados()).isEqualTo(11_400);
		assertThat(cenario.meses().getLast().adesao()).isEqualByComparingTo("100");
	}

	@Test
	void metaSemPrazoNaoTemMultaNemRitmoNecessario() {
		var semPrazo = base(null);
		var cenario = Simulador.projetar(semPrazo, new BigDecimal("81"), AGO.plusMonths(24),
				List.of(umDecimoDoFaturamento()));
		assertThat(cenario.multaTotal()).isZero();
		assertThat(cenario.meses()).noneMatch(Cenario.MesProjetado::aferido);
		assertThat(cenario.mesAtingimento()).isEqualTo(YearMonth.of(2027, 1));
		assertThat(Simulador.ligacoesNecessariasPorMes(semPrazo)).isNull();
	}

}
