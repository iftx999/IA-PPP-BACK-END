package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras RN-01 a RN-05 com os números do vídeo de demonstração (docs/03). */
class RegrasDeAdesaoTest {

	private static final Adesao CONCESSAO_A_AGO = new Adesao(8_761, 11_400);

	@Nested
	class AdesaoESemLigacao {

		@Test
		void concessaoAEmAgosto() {
			assertThat(CONCESSAO_A_AGO.percentual()).isEqualByComparingTo("76.8509");
			assertThat(CONCESSAO_A_AGO.semLigacao()).isEqualTo(2_639);
		}

		@Test
		void semImovelComRedeNaoTemPercentual() {
			assertThat(new Adesao(0, 0).percentual()).isNull();
		}

		@Test
		void recusaMaisLigadosQueComRede() {
			assertThatThrownBy(() -> new Adesao(10, 5)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void variacaoEmPontosPercentuais() {
			var julho = new Adesao(8_680, 11_400);
			assertThat(CONCESSAO_A_AGO.variacaoPp(julho)).isEqualByComparingTo("0.7105");
		}

	}

	@Nested
	class SituacaoDaUnidade {

		@Test
		void exemplosDoDemo() {
			assertThat(Situacao.classificar(new BigDecimal("76.9"), meta("80", "75"))).isEqualTo(Situacao.ATENCAO);
			assertThat(Situacao.classificar(new BigDecimal("68.0"), meta("75", "70")))
				.isEqualTo(Situacao.ABAIXO_DO_ALERTA);
			assertThat(Situacao.classificar(new BigDecimal("85.4"), meta("85", "80")))
				.isEqualTo(Situacao.META_ATINGIDA);
		}

		@Test
		void limitesSaoInclusivos() {
			assertThat(Situacao.classificar(new BigDecimal("80"), meta("80", "75"))).isEqualTo(Situacao.META_ATINGIDA);
			assertThat(Situacao.classificar(new BigDecimal("75"), meta("80", "75"))).isEqualTo(Situacao.ATENCAO);
		}

		@Test
		void semMeta() {
			assertThat(Situacao.classificar(new BigDecimal("50"), null)).isEqualTo(Situacao.SEM_META);
		}

	}

	@Nested
	class ProjecaoNoRitmo {

		/** Série real da Concessão A: rede cresceu de 10.500 para 11.400 em abr/2026. */
		private final List<PontoSerie> serie = serie(YearMonth.of(2025, 9),
				new long[] { 8011, 7959, 8035, 8053, 8081, 8158, 8130, 8357, 8414, 8602, 8680, 8761 },
				new long[] { 10500, 10500, 10500, 10500, 10500, 10500, 10500, 11400, 11400, 11400, 11400, 11400 });

		@Test
		void exemploDoDemo() {
			var p = Projecao.calcular(serie, new BigDecimal("80"), LocalDate.of(2026, 12, 31)).orElseThrow();

			assertThat(p.ritmoAtual()).isEqualByComparingTo("101");
			assertThat(p.mesesNaBase()).isEqualTo(4);
			assertThat(p.faltam()).isEqualTo(359);
			assertThat(p.mesesRestantes()).isEqualTo(4);
			assertThat(p.ritmoNecessario()).isEqualByComparingTo("89.8");
			assertThat(p.projecaoNoPrazo()).isEqualByComparingTo("80.3947");
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2026, 12));
			assertThat(p.atinge()).isTrue();
		}

		@Test
		void ritmoInsuficienteNaoAtingeNoPrazo() {
			var p = Projecao.calcular(serie, new BigDecimal("85"), LocalDate.of(2026, 12, 31)).orElseThrow();

			assertThat(p.faltam()).isEqualTo(929);
			assertThat(p.atinge()).isFalse();
			// 929 / 101 = 9,2 → 10 meses depois de ago/2026
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2027, 6));
		}

		@Test
		void metaSemFimInformaSoOMesDeAtingimento() {
			var p = Projecao.calcular(serie, new BigDecimal("80"), null).orElseThrow();

			assertThat(p.prazo()).isNull();
			assertThat(p.projecaoNoPrazo()).isNull();
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2026, 12));
			assertThat(p.atinge()).isTrue();
		}

		@Test
		void metaJaAtingida() {
			var p = Projecao.calcular(serie, new BigDecimal("70"), LocalDate.of(2026, 12, 31)).orElseThrow();

			assertThat(p.faltam()).isZero();
			assertThat(p.atinge()).isTrue();
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2026, 8));
		}

		@Test
		void janelaLimitadaASeisMeses() {
			var semMudanca = serie(YearMonth.of(2026, 1), new long[] { 100, 110, 120, 130, 140, 150, 160, 220 },
					new long[] { 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000 });
			var p = Projecao.calcular(semMudanca, new BigDecimal("50"), null).orElseThrow();

			assertThat(p.mesesNaBase()).isEqualTo(6);
			assertThat(p.ritmoAtual()).isEqualByComparingTo("18.3"); // base em fev (110):
																		// (220 - 110) / 6
		}

		@Test
		void redeMudouNoMesAtualFicaSemRitmo() {
			var mudouAgora = serie(YearMonth.of(2026, 7), new long[] { 500, 520 }, new long[] { 1000, 1200 });
			var p = Projecao.calcular(mudouAgora, new BigDecimal("80"), LocalDate.of(2026, 12, 31)).orElseThrow();

			assertThat(p.ritmoAtual()).isNull();
			assertThat(p.mesAtingimento()).isNull();
			assertThat(p.atinge()).isFalse();
		}

	}

	private static MetaVigente meta(String valor, String alerta) {
		return new MetaVigente(null, null, null, new BigDecimal(valor), new BigDecimal(alerta), null, null, null,
				false);
	}

	private static List<PontoSerie> serie(YearMonth inicio, long[] ligados, long[] comRede) {
		var pontos = new ArrayList<PontoSerie>();
		for (int i = 0; i < ligados.length; i++) {
			pontos.add(new PontoSerie(inicio.plusMonths(i), new Adesao(ligados[i], comRede[i])));
		}
		return pontos;
	}

}
