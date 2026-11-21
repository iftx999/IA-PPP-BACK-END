package dev.aurora.campanhas.model;

import java.util.List;

/**
 * Resultado da importação dos telefones dos imóveis (ADR 0052).
 *
 * @param removidos linhas com o telefone em branco: o imóvel deixa de receber aviso
 * @param rejeicoes as primeiras 100 linhas recusadas, com o motivo
 */
public record ImportacaoDeContatos(int linhas, int gravados, int removidos, int rejeitados, List<Rejeicao> rejeicoes) {

	public ImportacaoDeContatos {
		rejeicoes = List.copyOf(rejeicoes);
	}

	public record Rejeicao(int linha, String motivo) {
	}

}
