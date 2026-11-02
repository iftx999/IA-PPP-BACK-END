package dev.aurora.campanhas.model;

import java.util.UUID;

/** Imóvel sem ligação do recorte; {@code comOsAberta} fica de fora da campanha. */
public record Candidato(String matricula, UUID unidadeId, String situacao, boolean comOsAberta) {
}
