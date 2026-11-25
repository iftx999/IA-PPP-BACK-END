package dev.aurora.campanhas.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Envio de OS da campanha a uma equipe numa data. Sem lista: as planejadas (e devolvidas)
 * da campanha, na ordem de prioridade, até a quantidade (ou todas).
 */
public record Distribuicao(UUID equipeId, LocalDate data, List<UUID> ordens, @Nullable Prioridade prioridade,
		@Nullable Integer quantidade) {
}
