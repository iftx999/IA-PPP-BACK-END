package dev.aurora.logradouros.model;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/** Correção manual da faixa de numeração do trecho. */
public record DadosTrecho(@Nullable Integer numeroInicial, @Nullable Integer numeroFinal, Lado lado) {

	public void validar() {
		if ((numeroInicial != null && numeroInicial < 0) || (numeroFinal != null && numeroFinal < 0)) {
			throw new RegraVioladaException("A numeração não pode ser negativa.");
		}
		if (numeroInicial != null && numeroFinal != null && numeroInicial > numeroFinal) {
			throw new RegraVioladaException("O número inicial é maior que o final.");
		}
	}

}
