package dev.aurora.auditoria.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Filtros da consulta da trilha (S8). Período em [de, ate); unidade inclui a subárvore.
 */
public record FiltroAuditoria(@Nullable UUID usuarioId, List<String> tipos, @Nullable Instant de, @Nullable Instant ate,
		@Nullable UUID unidadeId) {

	public FiltroAuditoria {
		tipos = List.copyOf(tipos);
	}

}
