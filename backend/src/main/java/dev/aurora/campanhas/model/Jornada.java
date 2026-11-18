package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Jornada da equipe no dia (J8), aberta e fechada no app. */
public record Jornada(UUID id, UUID equipeId, String equipeNome, UUID usuarioId, Instant inicio,
		@Nullable Instant fim) {
}
