package dev.aurora.metas;

import java.util.List;
import java.util.UUID;

/**
 * Efeito de um aditivo nas metas (A5): a meta nova, as anteriores encerradas na véspera e
 * as substituídas por inteiro, e os itens contratuais de origem dessas metas.
 */
public record ResultadoAditivo(UUID metaId, int encerradas, int substituidas, List<UUID> itensSubstituidos) {

	public ResultadoAditivo {
		itensSubstituidos = List.copyOf(itensSubstituidos);
	}

}
