package dev.aurora.operacao.model;

import java.util.UUID;

/** Equipe pelo id, com a unidade e o nome. */
public record EquipeResumo(UUID id, UUID unidadeId, String nome) {
}
