package dev.aurora.contratos.model;

import java.util.List;

/** O que o extrator devolveu para um documento: propostas, modelo e versão do prompt. */
public record ResultadoExtracao(List<Proposta> propostas, String modelo, String versaoPrompt,
		List<String> observacoes) {

	public ResultadoExtracao {
		propostas = List.copyOf(propostas);
		observacoes = List.copyOf(observacoes);
	}

}
