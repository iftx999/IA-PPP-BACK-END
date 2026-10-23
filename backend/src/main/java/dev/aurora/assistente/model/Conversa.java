package dev.aurora.assistente.model;

import java.time.Instant;
import java.util.UUID;

public record Conversa(UUID id, String titulo, Instant criadaEm, Instant atualizadaEm) {
}
