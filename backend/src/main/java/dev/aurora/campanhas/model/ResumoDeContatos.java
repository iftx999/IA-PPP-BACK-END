package dev.aurora.campanhas.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Quantos imóveis do escopo têm celular para o aviso de chegada e a última importação
 * (ADR 0052).
 */
public record ResumoDeContatos(long total, @Nullable Instant atualizadoEm) {
}
