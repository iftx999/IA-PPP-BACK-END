package dev.aurora.servicos.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.aurora.comum.RegraVioladaException;

/**
 * Código de itens do catálogo (grupo, serviço, motivo, veículo, habilidade): maiúsculas,
 * sem acento, palavras separadas por "_". É a chave estável para integrações; o nome pode
 * mudar.
 */
public final class Codigo {

	private static final Pattern VALIDO = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

	private Codigo() {
	}

	/** "Limpeza de PV" → "LIMPEZA_DE_PV". */
	public static String normalizar(String texto) {
		var semAcento = Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		var codigo = semAcento.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (!VALIDO.matcher(codigo).matches()) {
			throw new RegraVioladaException(
					"Código inválido: use de 2 a 40 letras, números ou \"_\", começando por uma letra.");
		}
		return codigo;
	}

}
