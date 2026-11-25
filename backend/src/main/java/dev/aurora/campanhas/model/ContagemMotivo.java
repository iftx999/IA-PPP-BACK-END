package dev.aurora.campanhas.model;

/**
 * Quantas OS terminaram (ou foram devolvidas) com cada motivo do catálogo (I4).
 *
 * @param tipo NAO_EXECUCAO ou DEVOLUCAO
 */
public record ContagemMotivo(String tipo, String codigo, String nome, long quantidade) {
}
