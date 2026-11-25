package dev.aurora.campanhas.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Filtros das listas de OS (da campanha e a geral da programação).
 *
 * @param busca número da OS ou matrícula exata
 */
public record FiltroOrdens(@Nullable UUID campanhaId, @Nullable StatusOrdem status, @Nullable UUID equipeId,
		@Nullable Conferencia conferencia, @Nullable Prioridade prioridade, boolean somenteAtrasadas,
		boolean somenteAbertas, @Nullable UUID servicoId, @Nullable UUID unidadeId, @Nullable UUID loteId,
		@Nullable OrigemOrdem origem, @Nullable LocalDate dataProgramada, @Nullable String busca) {

	public static FiltroOrdens daCampanha(UUID campanha, @Nullable StatusOrdem status, @Nullable UUID equipe,
			@Nullable Conferencia conferencia, @Nullable Prioridade prioridade, boolean atrasadas) {
		return new FiltroOrdens(campanha, status, equipe, conferencia, prioridade, atrasadas, false, null, null, null,
				null, null, null);
	}

}
