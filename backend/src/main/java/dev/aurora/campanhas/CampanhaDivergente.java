package dev.aurora.campanhas;

import java.util.UUID;

/**
 * Campanha com OS executadas que a carga não confirmou (RN-19), base do alerta
 * {@code ORDEM_DIVERGENTE}.
 */
public record CampanhaDivergente(UUID campanhaId, String nome, UUID unidadeId, long divergentes) {
}
