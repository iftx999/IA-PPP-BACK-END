package dev.aurora.metas.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Meta própria manual (C3). Fim nulo = "sem fim". */
public record NovaMeta(UUID unidadeId, BigDecimal valor, BigDecimal alerta, LocalDate vigenciaInicio,
		@Nullable LocalDate vigenciaFim) {
}
