package dev.aurora.imoveis.model;

import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Filtros da lista (D1): unidade (e descendentes), competência, situação e início da
 * matrícula.
 */
public record FiltroSemLigacao(UUID unidadeId, YearMonth competencia, @Nullable SituacaoImovel situacao,
		@Nullable String matricula) {
}
