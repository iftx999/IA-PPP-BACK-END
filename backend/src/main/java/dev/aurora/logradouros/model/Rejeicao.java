package dev.aurora.logradouros.model;

/**
 * Item do arquivo recusado.
 *
 * @param posicao linha do CSV ou número da feição no GeoJSON (a partir de 1)
 */
public record Rejeicao(int posicao, String motivo) {
}
