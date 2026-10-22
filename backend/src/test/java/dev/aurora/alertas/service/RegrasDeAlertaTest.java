package dev.aurora.alertas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.Severidade;
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

	private static SituacaoDaUnidade situacao(String nome, String tipo, String adesao, String meta, String alerta,
			boolean propria, Situacao situacao) {
		return new SituacaoDaUnidade(UUID.randomUUID(), nome, tipo, new BigDecimal(adesao), YearMonth.of(2026, 8),
				new BigDecimal(meta), new BigDecimal(alerta), propria, situacao);
	}

}
