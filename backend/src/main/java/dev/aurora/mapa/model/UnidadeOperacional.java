package dev.aurora.mapa.model;

import java.util.UUID;

/** ETE ou EEE com coordenadas. */
public record UnidadeOperacional(UUID unidadeId, String tipo, String nome, double longitude, double latitude) {
}
