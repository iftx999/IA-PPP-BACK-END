package dev.aurora.contratos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Repetição de um prazo ou obrigação contratual (ADR 0026), em meses. Os vencimentos
 * seguintes somam o período ao primeiro (fim de mês ajustado: 31/01 + 1 mês = 28 ou
 * 29/02).
 */
public enum Recorrencia {

	MENSAL(1), BIMESTRAL(2), TRIMESTRAL(3), QUADRIMESTRAL(4), SEMESTRAL(6), ANUAL(12), QUADRIENAL(48), QUINQUENAL(60);

	private static final Pattern A_CADA = Pattern.compile("a cada (\\d+) (mes|meses|ano|anos)\\b");

	private final int meses;

	Recorrencia(int meses) {
		this.meses = meses;
	}

	public int meses() {
		return meses;
	}

	/** Para textos: "mensalmente", "a cada 4 anos"... */
	public String rotulo() {
		return switch (this) {
			case MENSAL -> "mensalmente";
			case BIMESTRAL -> "a cada 2 meses";
			case TRIMESTRAL -> "a cada 3 meses";
			case QUADRIMESTRAL -> "a cada 4 meses";
			case SEMESTRAL -> "a cada 6 meses";
			case ANUAL -> "anualmente";
			case QUADRIENAL -> "a cada 4 anos";
			case QUINQUENAL -> "a cada 5 anos";
		};
	}

	/** Duração aproximada do período em dias (para antecedência de alertas). */
	public int diasAproximados() {
		return meses * 30;
	}

	/**
	 * Sugestão a partir do texto livre da periodicidade (como a IA ou a pessoa escreveu:
	 * "mensal", "trimestralmente", "a cada 4 anos"). Frequências que não são vencimentos
	 * (ex.: "diária", de multas) não têm sugestão.
	 */
	public static Optional<Recorrencia> sugerir(@Nullable String periodicidade) {
		if (periodicidade == null || periodicidade.isBlank()) {
			return Optional.empty();
		}
		var texto = Normalizer.normalize(periodicidade, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.strip();
		var aCada = A_CADA.matcher(texto);
		if (aCada.find()) {
			var n = Integer.parseInt(aCada.group(1));
			var meses = aCada.group(2).startsWith("ano") ? n * 12 : n;
			for (var r : values()) {
				if (r.meses == meses) {
					return Optional.of(r);
				}
			}
			return Optional.empty();
		}
		for (var r : values()) {
			// Raiz sem o "al" final: "mensal", "mensalmente", "mensais", "anual",
			// "anuais"...
			var raiz = r.name().toLowerCase(Locale.ROOT).replaceAll("al$", "");
			if (texto.startsWith(raiz)) {
				return Optional.of(r);
			}
		}
		return Optional.empty();
	}

}
