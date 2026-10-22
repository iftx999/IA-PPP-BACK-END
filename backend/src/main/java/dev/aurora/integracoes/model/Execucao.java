package dev.aurora.integracoes.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Uma carga (execução de sincronização) de uma fonte. */
public record Execucao(UUID id, Instant inicio, @Nullable Instant fim, String situacao, @Nullable String origem,
		@Nullable LocalDate competencia, @Nullable Long lidos, @Nullable Long gravados, long rejeitados, long novos,
		long atualizados, long inalterados, @Nullable String erro) {
}
