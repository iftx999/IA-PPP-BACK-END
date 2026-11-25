package dev.aurora.campanhas.model;

import java.util.UUID;

/**
 * Imóvel sem ligação do recorte; {@code comOsAberta} (do serviço que a campanha geraria)
 * fica de fora.
 */
public record Candidato(String matricula, UUID unidadeId, String situacao, boolean comOsAberta) {
}
