package dev.aurora.integracoes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Fonte atrasada: sem carga concluída desde {@code ultimaCarga} (nula = nunca carregou).
 */
public record FonteAtrasada(UUID id, String nome, @Nullable Instant ultimaCarga, Duration atrasoMaximo) {
}
