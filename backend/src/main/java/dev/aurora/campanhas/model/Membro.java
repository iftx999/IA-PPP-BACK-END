package dev.aurora.campanhas.model;

import java.util.UUID;

/** Pessoa de uma equipe (usuário do tenant). */
public record Membro(UUID usuarioId, String nome, String email, boolean ativo) {
}
