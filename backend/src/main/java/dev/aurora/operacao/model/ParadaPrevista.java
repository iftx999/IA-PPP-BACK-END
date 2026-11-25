package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Parada da rota publicada com a situação atual da OS, para estimar o atraso.
 *
 * @param fimEm quando a OS saiu da rota (encerrada, devolvida, cancelada), se saiu
 */
public record ParadaPrevista(UUID osId, int ordem, Instant chegadaPrevista, Instant saidaPrevista, int minutosAte,
		String status, @Nullable Instant iniciadaEm, @Nullable Instant fimEm) {
}
