package dev.aurora.auditoria.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Evento como a consulta o mostra: com nome de quem fez, do recurso afetado e da unidade
 * relacionada, quando houver.
 */
public record EventoConsultado(long sequencia, Instant ocorridoEm, String tipo, @Nullable String recurso,
		@Nullable String recursoNome, @Nullable UUID usuarioId, @Nullable String usuarioNome,
		@Nullable String usuarioEmail, @Nullable String ip, @Nullable UUID unidadeId, @Nullable String unidadeNome,
		String detalhes) {
}
