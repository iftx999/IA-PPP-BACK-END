package dev.aurora.campanhas.model;

/**
 * Alvo pedido que não virou OS, com o motivo.
 *
 * @param alvo matrícula ou código do trecho
 */
public record Ignorada(String alvo, String motivo) {
}
