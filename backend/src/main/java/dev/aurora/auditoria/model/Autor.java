package dev.aurora.auditoria.model;

import java.util.UUID;

/** Pessoa que aparece na trilha, para o filtro por usuário. */
public record Autor(UUID id, String nome, String email) {
}
