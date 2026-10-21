package dev.aurora.metas.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Meta vigente de uma unidade. {@code origem} é o rótulo da cláusula (nulo = manual);
 * {@code doContrato} indica que veio de item contratual aprovado.
 */
public record MetaDaUnidade(UUID id, BigDecimal valor, @Nullable BigDecimal alerta, LocalDate vigenciaInicio,
		@Nullable LocalDate vigenciaFim, @Nullable String origem, boolean doContrato) {
}
