package dev.aurora.auditoria.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Evento como está gravado, para a verificação da cadeia. {@code formatoHash} nulo =
 * legado.
 */
public record EventoGravado(UUID id, UUID tenantId, long sequencia, Instant ocorridoEm, @Nullable UUID usuarioId,
		String tipo, @Nullable String recurso, String detalhes, @Nullable String ipOrigem, byte[] hashAnterior,
		byte[] hash, @Nullable Integer formatoHash) {
}
