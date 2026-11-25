package dev.aurora.logradouros.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Importação registrada no histórico. */
public record Importacao(UUID id, TipoImportacao tipo, @Nullable String unidadeNome, String arquivo, int lidos,
		int novos, int atualizados, int rejeitados, String feitaPor, Instant feitaEm) {
}
