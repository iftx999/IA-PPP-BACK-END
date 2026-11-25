package dev.aurora.logradouros.model;

import org.jspecify.annotations.Nullable;

/**
 * Imóvel vinculado ao logradouro.
 *
 * @param matricula inteira só para quem pode ver dados pessoais; senão mascarada
 * @param precisao precisão do ponto no mapa (RN-09); nulo sem ponto
 */
public record ImovelNoLogradouro(String matricula, @Nullable String numero, @Nullable String complemento,
		@Nullable String trecho, @Nullable String precisao) {

	public ImovelNoLogradouro comMatricula(String outra) {
		return new ImovelNoLogradouro(outra, numero, complemento, trecho, precisao);
	}

}
