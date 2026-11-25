package dev.aurora.campanhas;

import java.time.Instant;
import java.util.UUID;

/** Jornada aberta da pessoa no app (J8), para os demais módulos. */
public record JornadaAberta(UUID jornadaId, UUID equipeId, Instant inicio) {
}
