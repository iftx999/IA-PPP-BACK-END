package dev.aurora.campanhas.model;

import java.util.UUID;

/**
 * Prévia do lote (J5): quantos imóveis do logradouro (ou da faixa) entram e quantos já
 * têm OS aberta do serviço.
 *
 * @param unidadeId município do logradouro
 * @param limite máximo de OS por lote (RN-24)
 */
public record PreviaLote(UUID logradouroId, String logradouroNome, UUID unidadeId, long imoveis, long comOsAberta,
		long novas, int limite) {
}
