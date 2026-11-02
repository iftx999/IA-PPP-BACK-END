package dev.aurora.campanhas.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Envio de OS a uma equipe. Sem lista: as planejadas da campanha, na ordem de prioridade,
 * até a quantidade (ou todas).
 */
public record Distribuicao(UUID equipeId, List<UUID> ordens, @Nullable Prioridade prioridade,
		@Nullable Integer quantidade) {
}
