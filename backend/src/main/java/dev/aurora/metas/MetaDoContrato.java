package dev.aurora.metas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Meta vinda de item contratual aprovado. {@code origem} é o rótulo exibido (ex.:
 * "Contrato 001/2025, cláusula 7.2, p. 2").
 */
public record MetaDoContrato(UUID unidadeId, BigDecimal valor, BigDecimal alerta, LocalDate vigenciaInicio,
		@Nullable LocalDate vigenciaFim, UUID itemContratualId, String origem) {
}
