package dev.aurora.logradouros.model;

/**
 * Quanto do cadastro de imóveis já tem endereço para o campo.
 *
 * @param aproximados sem ponto no mapa ou com ponto de precisão "logradouro"/"bairro"
 */
public record Cobertura(long imoveis, long comEndereco, long comTrecho, long aproximados) {
}
