package dev.aurora.operacao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mapa do dia (J12): equipes, OS e quantas sugestões de reotimização estão abertas. */
public record MapaDoDia(LocalDate data, Instant geradoEm, List<EquipeNoDia> equipes, List<OrdemNoDia> ordens,
		int sugestoesAbertas) {

	public MapaDoDia {
		equipes = List.copyOf(equipes);
		ordens = List.copyOf(ordens);
	}

}
