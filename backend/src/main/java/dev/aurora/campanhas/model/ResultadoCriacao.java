package dev.aurora.campanhas.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * OS criadas de uma vez (avulsas, de trecho ou lote).
 *
 * @param loteId só no lote
 * @param ignoradas até as 200 primeiras
 * @param totalIgnoradas todas as que ficaram de fora
 */
public record ResultadoCriacao(@Nullable UUID loteId, int criadas, List<Ignorada> ignoradas, int totalIgnoradas) {
}
