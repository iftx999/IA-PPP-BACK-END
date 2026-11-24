package dev.aurora.campanhas.model;

/**
 * O que pede atenção de quem programa (página inicial).
 *
 * @param emergenciais em aberto
 * @param planejadas sem equipe
 * @param programadasHoje em aberto, com a data de hoje
 * @param conflitos de campo à espera de decisão
 */
public record ResumoOrdens(long abertas, long emergenciais, long atrasadas, long devolvidas, long planejadas,
		long programadasHoje, long conflitos) {
}
