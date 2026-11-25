package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Fato encontrado pelo detector antes de virar sugestão.
 *
 * @param chave o que identifica o fato (o evento do app ou a própria OS)
 */
public record Fato(UUID chave, UUID unidadeId, @Nullable UUID equipeId, @Nullable String equipeNome, UUID osId,
		long osNumero, @Nullable String motivo, @Nullable Instant momento) {
}
