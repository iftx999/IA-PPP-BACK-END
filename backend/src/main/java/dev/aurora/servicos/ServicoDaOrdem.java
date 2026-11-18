package dev.aurora.servicos;

import java.util.UUID;

/**
 * Serviço do catálogo como a OS o usa (RN-20, RN-21): a versão atual e os prazos por
 * prioridade.
 *
 * @param alvo IMOVEL, TRECHO ou AMBOS
 * @param unidadeMedida UN, M, M2 ou M3
 * @param conferidoPelaCarga RN-19: a carga mensal confirma a execução
 */
public record ServicoDaOrdem(UUID id, UUID versaoId, int versao, String codigo, String nome, String grupoNome,
		String alvo, String unidadeMedida, boolean conferidoPelaCarga, boolean ativo, int prazoEmergencialHoras,
		int prazoAltaDias, int prazoMediaDias, int prazoBaixaDias) {

	public boolean aceitaImovel() {
		return "IMOVEL".equals(alvo) || "AMBOS".equals(alvo);
	}

	public boolean aceitaTrecho() {
		return "TRECHO".equals(alvo) || "AMBOS".equals(alvo);
	}

}
