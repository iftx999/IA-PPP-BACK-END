package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Logradouro inteiro ou faixa de numeração de um lote (J5, RN-24). Sem faixa entram
 * também os imóveis sem número.
 */
public record FaixaDoLote(UUID logradouroId, @Nullable Integer numeroInicial, @Nullable Integer numeroFinal,
		LadoDaFaixa lado) {

	public boolean comFaixa() {
		return numeroInicial != null || numeroFinal != null;
	}

}
