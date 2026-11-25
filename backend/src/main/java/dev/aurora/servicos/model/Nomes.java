package dev.aurora.servicos.model;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/** Limpeza e limites de textos do catálogo. */
final class Nomes {

	private Nomes() {
	}

	static String obrigatorio(@Nullable String texto, int minimo, int maximo, String campo) {
		var limpo = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
		if (limpo.length() < minimo || limpo.length() > maximo) {
			throw new RegraVioladaException(campo + ": de " + minimo + " a " + maximo + " caracteres.");
		}
		return limpo;
	}

	static @Nullable String opcional(@Nullable String texto, int maximo, String campo) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		var limpo = texto.strip();
		if (limpo.length() > maximo) {
			throw new RegraVioladaException(campo + ": no máximo " + maximo + " caracteres.");
		}
		return limpo;
	}

}
