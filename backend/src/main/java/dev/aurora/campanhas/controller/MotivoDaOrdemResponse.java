package dev.aurora.campanhas.controller;

import java.util.UUID;

import dev.aurora.servicos.MotivoDaOrdem;
import org.jspecify.annotations.Nullable;

/**
 * Motivo do catálogo para as ações da OS (RN-22).
 *
 * @param tipo PARALISACAO, DEVOLUCAO, NAO_EXECUCAO ou CANCELAMENTO
 * @param servicoId nulo: vale para todos os serviços
 */
record MotivoDaOrdemResponse(UUID id, String tipo, String codigo, String nome, boolean exigeObservacao,
		@Nullable UUID servicoId) {

	static MotivoDaOrdemResponse de(MotivoDaOrdem m) {
		return new MotivoDaOrdemResponse(m.id(), m.tipo(), m.codigo(), m.nome(), m.exigeObservacao(), m.servicoId());
	}

}
