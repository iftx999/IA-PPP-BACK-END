package dev.aurora.alertas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.ProjecaoDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.contratos.PrazoContratual;
import dev.aurora.contratos.Recorrencia;
import dev.aurora.metas.SituacaoDaUnidade;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fronteiras das regras de alerta (RN-03, RN-05, RN-08 e prazos): cada limite de dias e
 * de percentual, e o que fica de fora. Mantém o PIT (perfil {@code mutacao}) honesto.
 */
class FronteirasDosAlertasTest {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

	@Nested
	class Adesao {

		@Test
		void ficamDeForaOperacionaisSemDadoSemMetaENaMeta() {
			var condicoes = RegrasDeAlerta.deAdesao(List.of(
					situacao("ETE Norte", "ETE", "50", "80", "70", true, Situacao.ABAIXO_DO_ALERTA,
							YearMonth.of(2026, 8)),
					new SituacaoDaUnidade(UUID.randomUUID(), "Sem dado", "MUNICIPIO", null, YearMonth.of(2026, 8),
							new BigDecimal("80"), new BigDecimal("70"), true, Situacao.SEM_META),
					new SituacaoDaUnidade(UUID.randomUUID(), "Sem meta", "MUNICIPIO", new BigDecimal("50"),
							YearMonth.of(2026, 8), null, null, true, Situacao.SEM_META),
					situacao("Na meta", "MUNICIPIO", "85", "80", "70", true, Situacao.META_ATINGIDA,
							YearMonth.of(2026, 8)),
					situacao("Sem meta aplicável", "MUNICIPIO", "85", "80", "70", true, Situacao.SEM_META,
							YearMonth.of(2026, 8))));
			assertThat(condicoes).isEmpty();
		}

		@Test
		void semLimiteDeAlertaNemCompetenciaADescricaoNaoOsCita() {
			var s = new SituacaoDaUnidade(UUID.randomUUID(), "Cidade C", "MUNICIPIO", new BigDecimal("75"), null,
					new BigDecimal("80"), null, true, Situacao.ATENCAO);
			var c = RegrasDeAlerta.deAdesao(List.of(s)).getFirst();
			assertThat(c.severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(c.descricao()).isEqualTo("Adesão de 75,0%; meta de 80,0%.");
			assertThat(c.titulo()).isEqualTo("Adesão abaixo da meta em Cidade C");
		}

	}

	@Nested
	class Ritmo {

		@Test
		void ficamDeForaOperacionaisQuemAtingeEOMesDoPrazo() {
			assertThat(RegrasDeAlerta.deRitmo(List.of(projecao("EEE 1", "EEE", "10", "60", "70", true, false, 4),
					projecao("Atinge", "MUNICIPIO", "10", "85", "70", true, true, 4),
					projecao("No prazo", "MUNICIPIO", "10", "60", "70", true, false, 0))))
				.isEmpty();
		}

		@Test
		void criticoSoAbaixoDoLimiteDeAlerta() {
			assertThat(severidade(projecao("No limite", "MUNICIPIO", "10", "70", "70", true, false, 4)))
				.isEqualTo(Severidade.ATENCAO);
			assertThat(severidade(projecao("Abaixo", "MUNICIPIO", "10", "69.9", "70", true, false, 4)))
				.isEqualTo(Severidade.CRITICO);
			assertThat(severidade(projecao("Sem alerta", "MUNICIPIO", "10", "10", null, true, false, 4)))
				.isEqualTo(Severidade.ATENCAO);
		}

		@Test
		void semRitmoPositivoDizQueNaoHaNovasLigacoes() {
			assertThat(descricao(projecao("Zero", "MUNICIPIO", "0", "60", "50", true, false, 4)))
				.startsWith("Sem novas ligações no período comparável; a adesão fica em 68,0%");
			assertThat(descricao(projecao("Nulo", "MUNICIPIO", null, "60", "50", true, false, 4)))
				.startsWith("Sem novas ligações");
			assertThat(descricao(projecao("Positivo", "MUNICIPIO", "1", "60", "50", true, false, 4)))
				.startsWith("No ritmo atual (1/mês), a adesão chega a 60,0% em dez/2026");
		}

		@Test
		void semRitmoNecessarioNemMesDeAtingimentoADescricaoNaoOsCita() {
			var semExtras = new ProjecaoDaUnidade(UUID.randomUUID(), "Cidade D", "MUNICIPIO", YearMonth.of(2026, 8),
					new BigDecimal("68"), new BigDecimal("80"), new BigDecimal("70"), true, BigDecimal.ONE, null, 120,
					4, new BigDecimal("75"), YearMonth.of(2026, 12), null, false);
			assertThat(RegrasDeAlerta.deRitmo(List.of(semExtras)).getFirst().descricao())
				.doesNotContain("ritmo necessário")
				.doesNotContain("a meta sai em")
				.doesNotContain("herdada")
				.endsWith("Faltam 120 ligações.");
		}

	}

	@Nested
	class Prazos {

		@Test
		void itemEntraSessentaDiasAntesECriticoComQuinze() {
			assertThat(RegrasDeAlerta.dePrazos(List.of(item(61, null)), HOJE)).isEmpty();
			assertThat(unica(item(60, null)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(item(16, null)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(item(15, null)).severidade()).isEqualTo(Severidade.CRITICO);
		}

		@Test
		void fimDoContratoEntraCentoEOitentaDiasAntesCriticoComTrintaESaiDepoisDeTerminar() {
			assertThat(RegrasDeAlerta.dePrazos(List.of(fimDoContrato(181)), HOJE)).isEmpty();
			assertThat(unica(fimDoContrato(180)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(fimDoContrato(31)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(fimDoContrato(30)).severidade()).isEqualTo(Severidade.CRITICO);
			assertThat(unica(fimDoContrato(0)).descricao()).contains("vence hoje (01/11/2026)");
			assertThat(RegrasDeAlerta.dePrazos(List.of(fimDoContrato(-1)), HOJE)).isEmpty();
		}

		@Test
		void mensalTemAntecedenciaECriticidadeProporcionais() {
			// Período de ~30 dias: antecedência de 15 e crítico a partir de 7.
			assertThat(RegrasDeAlerta.dePrazos(List.of(item(16, Recorrencia.MENSAL)), HOJE)).isEmpty();
			assertThat(unica(item(15, Recorrencia.MENSAL)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(item(8, Recorrencia.MENSAL)).severidade()).isEqualTo(Severidade.ATENCAO);
			assertThat(unica(item(7, Recorrencia.MENSAL)).severidade()).isEqualTo(Severidade.CRITICO);
		}

		@Test
		void textosDeVencidoHojeEDiasNoSingular() {
			var vencido = unica(item(-1, null));
			assertThat(vencido.titulo()).startsWith("Prazo contratual vencido: ");
			assertThat(vencido.descricao()).contains("venceu em 31/10/2026 (há 1 dia)");
			assertThat(unica(item(0, null)).descricao()).contains("vence hoje (01/11/2026)");
			// No dia do vencimento ainda não está vencido.
			assertThat(unica(item(0, null)).titulo()).isEqualTo("Prazo contratual: Relatório");
			assertThat(unica(item(1, null)).descricao()).contains("(faltam 1 dia)");
			assertThat(unica(item(2, null)).descricao()).contains("(faltam 2 dias)");
			assertThat(unica(item(2, null)).titulo()).startsWith("Prazo contratual: ");
		}

		@Test
		void semClausulaADescricaoNaoACita() {
			var semClausula = new PrazoContratual(UUID.randomUUID(), "002/2025", UUID.randomUUID(), "Relatório", null,
					UUID.randomUUID(), HOJE.plusDays(10), null);
			assertThat(unica(semClausula).descricao()).startsWith("Contrato 002/2025: vence em");
		}

	}

	@Test
	void duracaoEmDiasOuHorasComSingular() {
		assertThat(RegrasDeAlerta.duracao(Duration.ofHours(24))).isEqualTo("1 dia");
		assertThat(RegrasDeAlerta.duracao(Duration.ofHours(48))).isEqualTo("2 dias");
		assertThat(RegrasDeAlerta.duracao(Duration.ofHours(1))).isEqualTo("1 hora");
		assertThat(RegrasDeAlerta.duracao(Duration.ofHours(5))).isEqualTo("5 horas");
	}

	@Test
	void trilhaAlteradaECriticaEApontaOEvento() {
		var c = RegrasDeAlerta.trilhaAlterada(42);
		assertThat(c.severidade()).isEqualTo(Severidade.CRITICO);
		assertThat(c.descricao()).contains("evento nº 42");
		assertThat(c.link()).isEqualTo("/admin/auditoria");
	}

	private static SituacaoDaUnidade situacao(String nome, String tipo, String adesao, String meta, String alerta,
			boolean propria, Situacao situacao, @Nullable YearMonth competencia) {
		return new SituacaoDaUnidade(UUID.randomUUID(), nome, tipo, new BigDecimal(adesao), competencia,
				new BigDecimal(meta), new BigDecimal(alerta), propria, situacao);
	}

	private static ProjecaoDaUnidade projecao(String nome, String tipo, @Nullable String ritmo, String projecao,
			@Nullable String alerta, boolean propria, boolean atinge, int meses) {
		return new ProjecaoDaUnidade(UUID.randomUUID(), nome, tipo, YearMonth.of(2026, 8), new BigDecimal("68.0"),
				new BigDecimal("80"), alerta == null ? null : new BigDecimal(alerta), propria,
				ritmo == null ? null : new BigDecimal(ritmo), meses == 0 ? null : new BigDecimal("30"), 120, meses,
				new BigDecimal(projecao), YearMonth.of(2026, 12), null, atinge);
	}

	private static Severidade severidade(ProjecaoDaUnidade p) {
		return RegrasDeAlerta.deRitmo(List.of(p)).getFirst().severidade();
	}

	private static String descricao(ProjecaoDaUnidade p) {
		return RegrasDeAlerta.deRitmo(List.of(p)).getFirst().descricao();
	}

	private static PrazoContratual item(int dias, @Nullable Recorrencia recorrencia) {
		return new PrazoContratual(UUID.randomUUID(), "001/2025", UUID.randomUUID(), "Relatório", "9.1",
				UUID.randomUUID(), HOJE.plusDays(dias), recorrencia);
	}

	private static PrazoContratual fimDoContrato(int dias) {
		return new PrazoContratual(UUID.randomUUID(), "001/2025", null, "Fim da vigência", null, UUID.randomUUID(),
				HOJE.plusDays(dias), null);
	}

	private static Condicao unica(PrazoContratual prazo) {
		var condicoes = RegrasDeAlerta.dePrazos(List.of(prazo), HOJE);
		assertThat(condicoes).hasSize(1);
		return condicoes.getFirst();
	}

}
