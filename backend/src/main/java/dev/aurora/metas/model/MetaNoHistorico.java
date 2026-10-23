package dev.aurora.metas.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Versão anterior de uma meta alterada ou substituída por aditivo (A5). */
public record MetaNoHistorico(UUID metaId, LocalDate vigenciaInicio, @Nullable LocalDate vigenciaFim,
		BigDecimal valorMeta, @Nullable BigDecimal valorAlerta, @Nullable String origem, String motivo,
		@Nullable UUID substituidaPorMeta, Instant registradoEm) {
}
