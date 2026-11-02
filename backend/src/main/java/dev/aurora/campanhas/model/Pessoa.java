package dev.aurora.campanhas.model;

import java.util.UUID;

/** Usuário ativo do tenant que pode entrar numa equipe. */
public record Pessoa(UUID id, String nome, String email) {
}
