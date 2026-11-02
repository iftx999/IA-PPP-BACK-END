package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Impacto da campanha na meta da unidade (ADR 0036): adesão atual, adesão se todas as OS
 * virarem ligação e quantas das ligações que faltam a campanha cobre.
 *
 * @param faltamParaMeta ligações até a meta; nulo sem meta
 */
public record ImpactoNaMeta(UUID unidadeId, String unidadeNome, @Nullable YearMonth competencia, long ligados,
		long comRede, @Nullable BigDecimal adesao, @Nullable BigDecimal adesaoSeTodasLigarem, @Nullable BigDecimal meta,
		@Nullable Long faltamParaMeta) {
}
