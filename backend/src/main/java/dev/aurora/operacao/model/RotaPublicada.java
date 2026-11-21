package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Rota publicada mais recente da equipe no dia, com as paradas em ordem.
 *
 * @param confirmadaEm quando o agente confirmou a nova ordem no app (ADR 0050)
 */
public record RotaPublicada(UUID rotaId, UUID equipeId, Instant fimPrevisto, String origem,
		@Nullable Instant publicadaEm, @Nullable Instant confirmadaEm, List<ParadaPrevista> paradas) {

	/**
	 * Publicada no meio da jornada (não a do início) e ainda não confirmada pelo agente.
	 */
	public boolean aguardandoConfirmacao(@Nullable Instant inicioDaJornada) {
		return !"JORNADA".equals(origem) && confirmadaEm == null && publicadaEm != null && inicioDaJornada != null
				&& publicadaEm.isAfter(inicioDaJornada);
	}

	public RotaPublicada {
		paradas = List.copyOf(paradas);
	}

}
