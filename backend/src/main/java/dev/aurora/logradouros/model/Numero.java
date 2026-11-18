package dev.aurora.logradouros.model;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Número do imóvel como vem do cadastro ("123", "123A", "S/N"): texto para mostrar e a
 * parte inteira.
 */
public final class Numero {

	private static final Pattern INICIO = Pattern.compile("^(\\d{1,7})");

	private Numero() {
	}

	/** Parte numérica do início ("123A" → 123); nulo sem número ("S/N", "km 4"). */
	public static @Nullable Integer inteiro(@Nullable String numero) {
		if (numero == null) {
			return null;
		}
		var m = INICIO.matcher(numero.strip());
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

}
