package dev.aurora.logradouros.model;

import java.text.Normalizer;
import java.util.Locale;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/** Limpeza dos textos que vêm do cadastro da concessionária. */
public final class Textos {

	private Textos() {
	}

	public static @Nullable String opcional(@Nullable String texto, int maximo, String campo) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		var limpo = texto.strip().replaceAll("\\s+", " ");
		if (limpo.length() > maximo) {
			throw new RegraVioladaException(campo + ": no máximo " + maximo + " caracteres.");
		}
		return limpo;
	}

	/** Sem acento e em minúsculas: base da busca por nome. */
	public static String paraBusca(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("\\s+", " ")
			.strip();
	}

}
