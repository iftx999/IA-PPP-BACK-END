package dev.aurora.servicos.model;

/**
 * O parâmetro só aparece quando o parâmetro {@code chave} (sim/não ou lista, anterior)
 * tem o {@code valor} ("true"/"false" no sim/não; a opção na lista).
 */
public record Condicao(String chave, String valor) {
}
