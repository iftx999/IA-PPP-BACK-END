package dev.aurora.campanhas.model;

/**
 * @param foraDaArea OS cujo alvo fica fora da unidade onde a equipe atua (não enviadas)
 * @param incompativeis OS cujo serviço exige habilidade ou veículo que a equipe não tem
 * (RN-23; não enviadas)
 * @param ignoradas OS que não estavam em situação de programar (ex.: encerradas)
 */
public record ResultadoDistribuicao(int enviadas, int foraDaArea, int incompativeis, int ignoradas) {
}
