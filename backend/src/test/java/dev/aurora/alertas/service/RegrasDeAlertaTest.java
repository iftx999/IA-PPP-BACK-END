package dev.aurora.alertas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.ProjecaoDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.contratos.PrazoContratual;
import dev.aurora.integracoes.FonteAtrasada;
import dev.aurora.metas.SituacaoDaUnidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasDeAlertaTest {

	@Test
	void adesaoAbaixoDoAlertaECriticaEAbaixoDaMetaEAtencao() {
		var critica = situacao("Cidade B", "MUNICIPIO", "68.0", "75", "70", true, Situacao.ABAIXO_DO_ALERTA);
		var atencao = situacao("Concessão A", "CONCESSAO", "76.85", "80", "75", false, Situacao.ATENCAO);
		var ok = situacao("Cidade A", "MUNICIPIO", "85.4", "85", "80", true, Situacao.META_ATINGIDA);
		var ete = situacao("ETE 1", "ETE", "10", "85", "80", false, Situacao.ABAIXO_DO_ALERTA);

		var condicoes = RegrasDeAlerta.deAdesao(List.of(critica, atencao, ok, ete));

		assertThat(condicoes).extracting(Condicao::severidade).containsExactly(Severidade.CRITICO, Severidade.ATENCAO);
		assertThat(condicoes.get(0).titulo()).isEqualTo("Adesão abaixo do limite de alerta em Cidade B");
		assertThat(condicoes.get(0).descricao())
			.isEqualTo("Adesão de 68,0% em ago/2026; meta de 75,0% (alerta abaixo de 70,0%).");
		assertThat(condicoes.get(1).descricao()).endsWith("Meta herdada da unidade acima.");
		assertThat(condicoes.get(0).chave()).isEqualTo("ADESAO:" + critica.unidadeId());
		assertThat(condicoes.get(0).link()).isEqualTo("/painel?unidade=" + critica.unidadeId());
	}

	@Test
	void fonteAtrasadaComOuSemCarga() {
		var fuso = ZoneId.of("America/Sao_Paulo");
		var condicoes = RegrasDeAlerta.deFontes(
				List.of(new FonteAtrasada(UUID.randomUUID(), "Sistema comercial", Instant.parse("2026-09-26T15:00:00Z"),
						Duration.ofDays(2)), new FonteAtrasada(UUID.randomUUID(), "CNEFE", null, Duration.ofHours(36))),
				fuso);
		assertThat(condicoes.get(0).titulo()).isEqualTo("Fonte de dados atrasada: Sistema comercial");
		assertThat(condicoes.get(0).descricao())
			.startsWith("Última carga concluída em 26/09/2026 (atraso máximo: 2 dias)");
		assertThat(condicoes.get(1).descricao()).startsWith("Nenhuma carga concluída ainda (atraso máximo: 36 horas)");
		assertThat(condicoes).allMatch(c -> c.unidadeId() == null && c.link().equals("/admin/integracoes"));
	}

	@Test
	void ritmoAbaixoDaMetaEAtencaoEAbaixoDoAlertaECritico() {
		// RN-05 (doc 03): 101/mês leva a 80,4% em dez/2026, acima da meta: sem alerta.
		var noRitmo = projecao("Concessão A", "CONCESSAO", "101", "80.4", "80", "75", true, 359, 4, null);
		var atencao = projecao("Cidade A", "MUNICIPIO", "60", "78.2", "80", "75", false, 359, 4, YearMonth.of(2027, 2));
		var critica = projecao("Cidade B", "MUNICIPIO", "0", "68.0", "75", "70", false, 400, 4, null);
		var noPrazo = projecao("Setor", "SETOR_COMERCIAL", "10", "60", "80", "75", false, 100, 0, null);
		var ete = projecao("ETE 1", "ETE", "0", "10", "80", "75", false, 100, 4, null);

		var condicoes = RegrasDeAlerta.deRitmo(List.of(noRitmo, atencao, critica, noPrazo, ete));

		assertThat(condicoes).extracting(Condicao::severidade).containsExactly(Severidade.ATENCAO, Severidade.CRITICO);
		assertThat(condicoes.get(0).titulo()).isEqualTo("Meta fora do ritmo em Cidade A");
		assertThat(condicoes.get(0).descricao())
			.isEqualTo("No ritmo atual (60/mês), a adesão chega a 78,2% em dez/2026; meta de 80,0% até dez/2026. "
					+ "Faltam 359 ligações: ritmo necessário de 90/mês. No ritmo atual, a meta sai em fev/2027. "
					+ "Meta herdada da unidade acima.");
		assertThat(condicoes.get(1).descricao())
			.startsWith("Sem novas ligações no período comparável; a adesão fica em 68,0%; meta de 75,0%");
		assertThat(condicoes.get(0).chave()).isEqualTo("RITMO:" + atencao.unidadeId());
		assertThat(condicoes.get(0).link()).isEqualTo("/painel?unidade=" + atencao.unidadeId());
	}

	@Test
	void prazosDentroDaAntecedenciaEVencidosAteSeremCumpridos() {
		var hoje = LocalDate.of(2026, 10, 31);
		var contrato = UUID.randomUUID();
		var unidade = UUID.randomUUID();
		var longe = prazo(contrato, unidade, "Plano de obras", hoje.plusDays(61));
		var trintaDias = prazo(contrato, unidade, "Relatório anual", hoje.plusDays(30));
		var dezDias = prazo(contrato, unidade, "Cadastro de usuários", hoje.plusDays(10));
		var vencido = prazo(contrato, unidade, "Revisão tarifária", hoje.minusDays(3));
		var fim = new PrazoContratual(contrato, "001/2025", null, "Fim da vigência do contrato", null, unidade,
				hoje.plusDays(100));
		var fimPassado = new PrazoContratual(contrato, "002/2010", null, "Fim da vigência do contrato", null, unidade,
				hoje.minusDays(1));

		var condicoes = RegrasDeAlerta.dePrazos(List.of(longe, trintaDias, dezDias, vencido, fim, fimPassado), hoje);

		assertThat(condicoes).extracting(Condicao::severidade)
			.containsExactly(Severidade.ATENCAO, Severidade.CRITICO, Severidade.CRITICO, Severidade.ATENCAO);
		assertThat(condicoes.get(0).titulo()).isEqualTo("Prazo contratual: Relatório anual");
		assertThat(condicoes.get(0).descricao())
			.isEqualTo("Contrato 001/2025, cláusula 9.1: vence em 30/11/2026 (faltam 30 dias). "
					+ "Quando for cumprido, marque o item no contrato.");
		assertThat(condicoes.get(2).titulo()).isEqualTo("Prazo contratual vencido: Revisão tarifária");
		assertThat(condicoes.get(2).descricao()).contains("venceu em 28/10/2026 (há 3 dias)");
		assertThat(condicoes.get(3).chave()).isEqualTo("CONTRATO:" + contrato);
		assertThat(condicoes.get(3).titulo()).isEqualTo("Vigência do contrato 001/2025 termina em breve");
		assertThat(condicoes.get(1).chave()).isEqualTo("PRAZO:" + dezDias.itemId());
		assertThat(condicoes).allMatch(c -> c.unidadeId().equals(unidade) && c.link().equals("/contratos/" + contrato));
	}

	private static PrazoContratual prazo(UUID contrato, UUID unidade, String titulo, LocalDate data) {
		return new PrazoContratual(contrato, "001/2025", UUID.randomUUID(), titulo, "9.1", unidade, data);
	}

	private static ProjecaoDaUnidade projecao(String nome, String tipo, String ritmo, String projecao, String meta,
			String alerta, boolean atinge, long faltam, int meses, YearMonth atingimento) {
		return new ProjecaoDaUnidade(UUID.randomUUID(), nome, tipo, YearMonth.of(2026, 8), new BigDecimal("68.0"),
				new BigDecimal(meta), new BigDecimal(alerta), !nome.startsWith("Cidade A"), new BigDecimal(ritmo),
				meses == 0 ? null
						: BigDecimal.valueOf(faltam)
							.divide(BigDecimal.valueOf(meses), 1, java.math.RoundingMode.HALF_UP),
				faltam, meses, new BigDecimal(projecao), YearMonth.of(2026, 12), atingimento, atinge);
	}

	private static SituacaoDaUnidade situacao(String nome, String tipo, String adesao, String meta, String alerta,
			boolean propria, Situacao situacao) {
		return new SituacaoDaUnidade(UUID.randomUUID(), nome, tipo, new BigDecimal(adesao), YearMonth.of(2026, 8),
				new BigDecimal(meta), new BigDecimal(alerta), propria, situacao);
	}

}
