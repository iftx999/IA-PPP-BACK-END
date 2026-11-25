package dev.aurora.campanhas.model;

/**
 * Cancelamento de várias OS.
 *
 * @param ignoradas já com a equipe, encerradas, canceladas ou fora da área
 */
public record ResultadoCancelamento(int canceladas, int ignoradas) {
}
