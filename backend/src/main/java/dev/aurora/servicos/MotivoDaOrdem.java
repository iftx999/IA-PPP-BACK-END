package dev.aurora.servicos;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Motivo do catálogo usado nas ações da OS (RN-22).
 *
 * @param tipo PARALISACAO, DEVOLUCAO, NAO_EXECUCAO ou CANCELAMENTO
 * @param servicoId nulo: vale para todos os serviços
 */
public record MotivoDaOrdem(UUID id, String tipo, String codigo, String nome, boolean exigeObservacao,
		@Nullable UUID servicoId, boolean ativo) {

	/** O motivo é do tipo pedido, está ativo e vale para o serviço. */
	public boolean valePara(String tipoPedido, UUID servico) {
		return ativo && tipo.equals(tipoPedido) && (servicoId == null || servicoId.equals(servico));
	}

}
