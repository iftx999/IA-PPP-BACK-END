package dev.aurora.roteirizacao.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Pedido de cálculo das rotas de um dia.
 *
 * @param fixadas por equipe, as OS que ficam no começo da rota, nesta ordem (J6)
 * @param anteriorId roteirização que esta substitui (recálculo)
 */
public record PedidoRoteirizacao(UUID unidadeId, LocalDate data, OrigemRoteirizacao origem, List<UUID> equipes,
		List<UUID> ordens, Map<UUID, List<UUID>> fixadas, @Nullable UUID anteriorId) {

	public PedidoRoteirizacao {
		equipes = List.copyOf(equipes);
		ordens = List.copyOf(ordens);
		fixadas = Map.copyOf(fixadas);
	}

}
