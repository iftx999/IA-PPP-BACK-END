package dev.aurora.campanhas.model;

/**
 * @param foraDaArea OS cujo imóvel fica fora da unidade onde a equipe atua (não enviadas)
 */
public record ResultadoDistribuicao(int enviadas, int foraDaArea) {
}
