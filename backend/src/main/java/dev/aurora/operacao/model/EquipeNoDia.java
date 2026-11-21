package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Equipe no mapa do dia (J12).
 *
 * @param posicao última posição da jornada do dia (ou a do início)
 * @param atrasoMin atraso estimado em relação à rota publicada (negativo: adiantada);
 * nulo sem jornada aberta ou sem rota publicada
 */
public record EquipeNoDia(UUID id, String nome, UUID unidadeId, @Nullable UUID jornadaId,
		@Nullable Instant jornadaInicio, @Nullable Instant jornadaFim, @Nullable Ponto posicao,
		@Nullable Instant posicaoEm, @Nullable Double precisaoM, @Nullable Integer atrasoMin,
		@Nullable Instant fimPrevisto) {

	public boolean emJornada() {
		return jornadaId != null && jornadaFim == null;
	}

	public EquipeNoDia comAtraso(@Nullable Integer atraso, @Nullable Instant fim) {
		return new EquipeNoDia(id, nome, unidadeId, jornadaId, jornadaInicio, jornadaFim, posicao, posicaoEm, precisaoM,
				atraso, fim);
	}

}
