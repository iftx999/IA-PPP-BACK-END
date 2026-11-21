package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso de chegada ao morador (ADR 0052).
 *
 * @param situacao ENVIADO, REGISTRADO (sem provedor de SMS) ou FALHOU
 */
public record AvisoDeChegada(UUID id, UUID osId, int minutos, String situacao, Instant ocorridoEm, Instant criadoEm) {
}
