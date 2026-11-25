package dev.aurora.campanhas;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma jornada foi aberta pelo app (J8), publicada depois do commit: a roteirização
 * calcula a rota do dia da equipe a partir da posição do agente (D6, ADR 0048).
 *
 * @param usuarioId quem abriu (responde pela publicação automática da rota)
 */
public record JornadaIniciada(UUID tenantId, UUID jornadaId, UUID equipeId, UUID usuarioId, LocalDate data) {
}
