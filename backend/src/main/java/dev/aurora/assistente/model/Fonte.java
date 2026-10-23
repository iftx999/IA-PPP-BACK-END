package dev.aurora.assistente.model;

/**
 * Chip numerado da resposta (G2): de onde veio um número. Ex.: "Adesão à rede de esgoto"
 * · "Cidade A Centro · ago/2026".
 */
public record Fonte(int numero, String titulo, String detalhe) {
}
