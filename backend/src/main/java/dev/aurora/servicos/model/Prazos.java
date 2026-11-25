package dev.aurora.servicos.model;

import dev.aurora.comum.RegraVioladaException;

/**
 * Prazo do serviço por prioridade (RN-21): emergencial em horas corridas; alta, média e
 * baixa em dias úteis, nessa ordem crescente.
 */
public record Prazos(int emergencialHoras, int altaDias, int mediaDias, int baixaDias) {

	void validar() {
		if (emergencialHoras < 1 || emergencialHoras > 72) {
			throw new RegraVioladaException("Prazo emergencial: de 1 a 72 horas.");
		}
		for (var dias : new int[] { altaDias, mediaDias, baixaDias }) {
			if (dias < 1 || dias > 365) {
				throw new RegraVioladaException("Prazos em dias úteis: de 1 a 365.");
			}
		}
		if (altaDias > mediaDias || mediaDias > baixaDias) {
			throw new RegraVioladaException(
					"O prazo da prioridade alta não pode ser maior que o da média, nem o da média "
							+ "maior que o da baixa.");
		}
	}

}
