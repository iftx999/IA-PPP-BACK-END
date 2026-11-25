package dev.aurora.logradouros.model;

import java.util.Locale;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/** Lado da numeração coberto pelo trecho. */
public enum Lado {

	PAR, IMPAR, AMBOS;

	/** "par", "ímpar", "I", "P", vazio (ambos). */
	public static Lado de(@Nullable String texto) {
		if (texto == null || texto.isBlank()) {
			return AMBOS;
		}
		return switch (texto.strip().toUpperCase(Locale.ROOT).replace("Í", "I")) {
			case "PAR", "P" -> PAR;
			case "IMPAR", "I" -> IMPAR;
			case "AMBOS", "A" -> AMBOS;
			default -> throw new RegraVioladaException("Lado inválido: \"" + texto + "\" (use PAR, IMPAR ou AMBOS).");
		};
	}

}
