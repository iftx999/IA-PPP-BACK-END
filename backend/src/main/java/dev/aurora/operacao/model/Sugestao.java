package dev.aurora.operacao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Sugestão de reotimização aberta pelo detector (ADR 0049). */
public record Sugestao(UUID id, UUID unidadeId, LocalDate data, Gatilho gatilho, @Nullable UUID equipeId,
		@Nullable String equipeNome, @Nullable UUID osId, @Nullable Long osNumero, String descricao,
		@Nullable Integer atrasoMin, SituacaoSugestao situacao, Instant criadaEm, @Nullable Instant decididaEm,
		@Nullable UUID roteirizacaoId) {
}
