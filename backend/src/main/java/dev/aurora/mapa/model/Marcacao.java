package dev.aurora.mapa.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** E4: marcação de visita (ponto, texto, autor e data). */
public record Marcacao(UUID id, UUID unidadeId, String unidadeNome, double longitude, double latitude, String titulo,
		@Nullable String observacao, UUID autorId, String autorNome, Instant criadaEm) {
}
