package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import dev.aurora.adesao.Situacao;

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

	/**
	 * Fronteiras e casos-limite das regras (doc 09: testes de mutação com o PIT, perfil
	 * {@code mutacao}).
	 */
	@Nested
	class Fronteiras {

		@Test
		void adesaoRecusaNegativos() {
			assertThatThrownBy(() -> new Adesao(-1, 5)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new Adesao(0, -1)).isInstanceOf(IllegalArgumentException.class);
			assertThat(new Adesao(5, 5).percentual()).isEqualByComparingTo("100");
			assertThat(new Adesao(0, 5).percentual()).isEqualByComparingTo("0");
		}

		@Test
		void variacaoSemUmDosLadosENula() {
			assertThat(CONCESSAO_A_AGO.variacaoPp(null)).isNull();
			assertThat(CONCESSAO_A_AGO.variacaoPp(new Adesao(0, 0))).isNull();
			assertThat(new Adesao(0, 0).variacaoPp(CONCESSAO_A_AGO)).isNull();
			assertThat(CONCESSAO_A_AGO.variacaoPp(CONCESSAO_A_AGO)).isEqualByComparingTo("0");
		}

		@Test
		void situacaoSemDadoOuSemMeta() {
			assertThat(Situacao.classificar(new BigDecimal("50"), (MetaVigente) null)).isEqualTo(Situacao.SEM_META);
			assertThat(Situacao.classificar(null, meta("80", "75"))).isEqualTo(Situacao.SEM_META);
			assertThat(Situacao.classificar(new BigDecimal("50"), null, new BigDecimal("40")))
				.isEqualTo(Situacao.SEM_META);
			// Sem limite de alerta: abaixo da meta é atenção, nunca crítico.
			assertThat(Situacao.classificar(new BigDecimal("1"), new BigDecimal("80"), null))
				.isEqualTo(Situacao.ATENCAO);
			// No limite de alerta ainda é atenção; abaixo dele, crítico.
			assertThat(Situacao.classificar(new BigDecimal("75"), meta("80", "75"))).isEqualTo(Situacao.ATENCAO);
			assertThat(Situacao.classificar(new BigDecimal("74.9999"), meta("80", "75")))
				.isEqualTo(Situacao.ABAIXO_DO_ALERTA);
			assertThat(Situacao.classificar(new BigDecimal("79.9999"), meta("80", "75"))).isEqualTo(Situacao.ATENCAO);
		}

		@Test
		void projecaoSemDadosSuficientes() {
			assertThat(Projecao.calcular(List.of(), new BigDecimal("80"), null)).isEmpty();
			assertThat(Projecao.calcular(serie(YearMonth.of(2026, 1), new long[] { 1 }, new long[] { 2 }), null, null))
				.isEmpty();
			assertThat(Projecao.calcular(serie(YearMonth.of(2026, 1), new long[] { 0 }, new long[] { 0 }),
					new BigDecimal("80"), null))
				.isEmpty();
		}

		@Test
		void mesFaltandoNaSerieInterrompeAJanela() {
			var pontos = new ArrayList<PontoSerie>();
			pontos.add(new PontoSerie(YearMonth.of(2026, 1), new Adesao(100, 1000)));
			pontos.add(new PontoSerie(YearMonth.of(2026, 3), new Adesao(140, 1000)));
			pontos.add(new PontoSerie(YearMonth.of(2026, 4), new Adesao(150, 1000)));
			var p = Projecao.calcular(pontos, new BigDecimal("50"), null).orElseThrow();

			assertThat(p.mesesNaBase()).isEqualTo(1);
			assertThat(p.ritmoAtual()).isEqualByComparingTo("10");
		}

		@Test
		void janelaDeExatamenteSeisMesesUsaTodos() {
			var seteMeses = serie(YearMonth.of(2026, 1), new long[] { 100, 110, 120, 130, 140, 150, 160 },
					new long[] { 1000, 1000, 1000, 1000, 1000, 1000, 1000 });
			var p = Projecao.calcular(seteMeses, new BigDecimal("50"), null).orElseThrow();

			assertThat(p.mesesNaBase()).isEqualTo(6);
			assertThat(p.ritmoAtual()).isEqualByComparingTo("10");
		}

		@Test
		void prazoNoMesAtualOuJaPassadoNaoTemRitmoNecessario() {
			var dois = serie(YearMonth.of(2026, 7), new long[] { 500, 510 }, new long[] { 1000, 1000 });
			var noMes = Projecao.calcular(dois, new BigDecimal("80"), LocalDate.of(2026, 8, 31)).orElseThrow();
			assertThat(noMes.mesesRestantes()).isZero();
			assertThat(noMes.ritmoNecessario()).isNull();
			assertThat(noMes.projecaoNoPrazo()).isEqualByComparingTo("51");
			assertThat(noMes.atinge()).isFalse();

			var passado = Projecao.calcular(dois, new BigDecimal("80"), LocalDate.of(2026, 3, 31)).orElseThrow();
			assertThat(passado.mesesRestantes()).isZero();
		}

		@Test
		void ritmoZeroOuNegativoNaoTemMesDeAtingimento() {
			var parado = serie(YearMonth.of(2026, 1), new long[] { 500, 500 }, new long[] { 1000, 1000 });
			assertThat(Projecao.calcular(parado, new BigDecimal("80"), null).orElseThrow().mesAtingimento()).isNull();
			assertThat(Projecao.calcular(parado, new BigDecimal("80"), null).orElseThrow().atinge()).isFalse();

			var caindo = serie(YearMonth.of(2026, 1), new long[] { 520, 500 }, new long[] { 1000, 1000 });
			var p = Projecao.calcular(caindo, new BigDecimal("80"), LocalDate.of(2026, 12, 31)).orElseThrow();
			assertThat(p.ritmoAtual()).isEqualByComparingTo("-20");
			assertThat(p.mesAtingimento()).isNull();
			// Ritmo negativo não piora a projeção: fica no que já está ligado.
			assertThat(p.projecaoNoPrazo()).isEqualByComparingTo("50");
		}

		@Test
		void projecaoExatamenteNaMetaAtinge() {
			// 600 ligados, 50 por mês, 4 meses: 800 de 1000 = 80% no prazo.
			var serie = serie(YearMonth.of(2026, 7), new long[] { 550, 600 }, new long[] { 1000, 1000 });
			var p = Projecao.calcular(serie, new BigDecimal("80"), LocalDate.of(2026, 12, 31)).orElseThrow();
			assertThat(p.projecaoNoPrazo()).isEqualByComparingTo("80");
			assertThat(p.ritmoNecessario()).isEqualByComparingTo("50");
			assertThat(p.atinge()).isTrue();
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2026, 12));
		}

		@Test
		void metaJaAtingidaSemRitmoAtingeNoMesAtual() {
			var parado = serie(YearMonth.of(2026, 7), new long[] { 850, 850 }, new long[] { 1000, 1000 });
			var p = Projecao.calcular(parado, new BigDecimal("80"), null).orElseThrow();
			assertThat(p.faltam()).isZero();
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2026, 8));
			assertThat(p.atinge()).isTrue();
		}

		@Test
		void comPrazoAtingirDepoisDeleNaoConta() {
			// 10 por mês: a meta sai em 2028, mas o prazo é dez/2026.
			var lento = serie(YearMonth.of(2026, 7), new long[] { 590, 600 }, new long[] { 1000, 1000 });
			var p = Projecao.calcular(lento, new BigDecimal("80"), LocalDate.of(2026, 12, 31)).orElseThrow();
			assertThat(p.mesAtingimento()).isEqualTo(YearMonth.of(2028, 4));
			assertThat(p.atinge()).isFalse();
		}

		@Test
		void projecaoNaoPassaDeCemPorCento() {
			var serie = serie(YearMonth.of(2026, 7), new long[] { 500, 900 }, new long[] { 1000, 1000 });
			var p = Projecao.calcular(serie, new BigDecimal("95"), LocalDate.of(2026, 12, 31)).orElseThrow();
			assertThat(p.projecaoNoPrazo()).isEqualByComparingTo("100");
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
