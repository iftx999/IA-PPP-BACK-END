package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * OS no mapa do dia (J12).
 *
 * @param vencida aberta com o prazo (RN-21) já passado
 */
public record OrdemNoDia(UUID id, long numero, String servicoNome, String status, String prioridade,
		@Nullable UUID equipeId, @Nullable Integer sequenciaRota, @Nullable Ponto ponto, @Nullable Instant prazoEm,
		boolean vencida) {
}
