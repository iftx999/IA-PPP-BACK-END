package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Filtros da lista de OS de uma campanha. */
public record FiltroOrdens(@Nullable StatusOrdem status, @Nullable UUID equipeId, @Nullable Conferencia conferencia,
		@Nullable Prioridade prioridade, boolean somenteAtrasadas) {
}
