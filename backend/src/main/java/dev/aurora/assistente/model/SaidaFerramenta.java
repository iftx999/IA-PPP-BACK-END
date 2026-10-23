package dev.aurora.assistente.model;

/**
 * Resultado de uma ferramenta em JSON; {@code erro} = a entrada não serviu (ex.: unidade
 * desconhecida).
 */
public record SaidaFerramenta(String json, boolean erro) {
}
