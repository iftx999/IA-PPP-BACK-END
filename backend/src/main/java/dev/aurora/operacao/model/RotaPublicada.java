package dev.aurora.operacao.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rota publicada mais recente da equipe no dia, com as paradas em ordem. */
public record RotaPublicada(UUID rotaId, UUID equipeId, Instant fimPrevisto, List<ParadaPrevista> paradas) {

	public RotaPublicada {
		paradas = List.copyOf(paradas);
	}

}
