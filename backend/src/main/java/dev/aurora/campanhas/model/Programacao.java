package dev.aurora.campanhas.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** OS escolhidas para uma equipe numa data (programar ou reprogramar). */
public record Programacao(List<UUID> ordens, UUID equipeId, LocalDate data) {
}
