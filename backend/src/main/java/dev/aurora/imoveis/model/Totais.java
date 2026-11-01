package dev.aurora.imoveis.model;

/**
 * Contagens das abas Todos / Factíveis / Cortados e, dos cortados, quantos por motivo (o
 * restante não teve motivo informado na carga).
 */
public record Totais(long todos, long factiveis, long cortados, long cortadosPorInadimplencia, long cortadosAPedido) {
}
