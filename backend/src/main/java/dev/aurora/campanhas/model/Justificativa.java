package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Motivo do catálogo e observação de uma devolução ou de um cancelamento (RN-22). */
public record Justificativa(UUID motivoId, @Nullable String observacao) {
}
