package dev.aurora.logradouros.model;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/** Correção manual do logradouro (o código não muda: é a chave da reimportação). */
public record DadosLogradouro(@Nullable String tipo, String nome, @Nullable String bairro) {

	public DadosLogradouro normalizados() {
		var nomeLimpo = nome.strip().replaceAll("\\s+", " ");
		if (nomeLimpo.isEmpty() || nomeLimpo.length() > 120) {
			throw new RegraVioladaException("Nome do logradouro: de 1 a 120 caracteres.");
		}
		return new DadosLogradouro(Textos.opcional(tipo, 30, "Tipo"), nomeLimpo, Textos.opcional(bairro, 80, "Bairro"));
	}

}
