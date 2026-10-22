package dev.aurora.integracoes.model;

import java.time.Instant;

/**
 * Credencial do Agente Aurora: só o prefixo é exibido; o segredo não é guardado (apenas o
 * hash).
 */
public record Credencial(String prefixo, Instant criadaEm, boolean ativa) {
}
