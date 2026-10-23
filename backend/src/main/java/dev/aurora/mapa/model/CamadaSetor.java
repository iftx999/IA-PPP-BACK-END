package dev.aurora.mapa.model;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Setor comercial com o polígono (gerado, desenhado ou importado). */
public record CamadaSetor(UUID unidadeId, String nome, String origem, JsonNode geometria) {
}
