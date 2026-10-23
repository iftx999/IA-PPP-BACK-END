package dev.aurora.metas.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Meta vigente que cruza o período de um aditivo; {@code itemContratualId} nulo = meta
 * manual.
 */
public record MetaSobreposta(UUID id, String unidadeNome, LocalDate vigenciaInicio, @Nullable LocalDate vigenciaFim,
		@Nullable UUID itemContratualId) {
}
