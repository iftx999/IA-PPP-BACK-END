package dev.aurora.servicos.model;

import java.util.UUID;

/** Habilidade exigida por serviços; a equipe precisa tê-la para receber a OS (RN-23). */
public record Habilidade(UUID id, String codigo, String nome, boolean ativo) {
}
