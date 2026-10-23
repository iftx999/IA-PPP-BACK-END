package dev.aurora.assistente.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * G6, "nunca inventar número": cada número da resposta precisa aparecer no que as
 * ferramentas devolveram, aceitando a escrita brasileira (85,41 · 4.954) e o
 * arredondamento (85,4 vale para 85,41). O que não bate é guardado e a tela avisa.
 * Inteiros de um dígito ficam de fora (contagens como "3 setores" e ordinais), assim como
 * as marcas de fonte [n].
 */
final class VerificadorDeNumeros {

	/** Número com separadores; não pega o que está dentro de [n]. */
	private static final Pattern NUMERO = Pattern.compile("(?<![\\[\\d.,])-?\\d+(?:[.,]\\d+)*(?![\\]\\d])");

	private static final Pattern NUMERO_JSON = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	private static final Pattern MARCA_DE_FONTE = Pattern.compile("\\[\\d+]");

	private VerificadorDeNumeros() {
	}

	/**
	 * Números do texto (como escritos) sem correspondência nas saídas das ferramentas.
	 */
	static List<String> semFonte(String texto, List<String> saidasDasFerramentas) {
		var conhecidos = new LinkedHashSet<BigDecimal>();
		for (var saida : saidasDasFerramentas) {
			var m = NUMERO_JSON.matcher(saida);
			while (m.find()) {
				conhecidos.add(new BigDecimal(m.group()));
			}
		}
		var semFonte = new ArrayList<String>();
		var m = NUMERO.matcher(MARCA_DE_FONTE.matcher(texto).replaceAll(" "));
		while (m.find()) {
			var escrito = m.group();
			var valor = interpretar(escrito);
			if (valor == null || (valor.scale() <= 0 && valor.abs().compareTo(BigDecimal.TEN) < 0)) {
				continue;
			}
			if (!confere(valor, conhecidos) && !semFonte.contains(escrito)) {
				semFonte.add(escrito);
			}
		}
		return semFonte;
	}

	/**
	 * "85,41" → 85.41; "4.954" e "1.234.567" → milhar; "85.41" → decimal (escrita da
	 * API); "4.954,5" → 4954.5.
	 */
	static @Nullable BigDecimal interpretar(String escrito) {
		try {
			if (escrito.contains(",")) {
				return new BigDecimal(escrito.replace(".", "").replace(',', '.'));
			}
			if (escrito.matches("-?\\d{1,3}(\\.\\d{3})+")) {
				return new BigDecimal(escrito.replace(".", ""));
			}
			return new BigDecimal(escrito);
		}
		catch (NumberFormatException erro) {
			return null;
		}
	}

	/** O valor escrito é algum conhecido, igual ou arredondado às casas escritas. */
	private static boolean confere(BigDecimal escrito, Set<BigDecimal> conhecidos) {
		int casas = Math.max(0, escrito.scale());
		for (var conhecido : conhecidos) {
			if (conhecido.compareTo(escrito) == 0) {
				return true;
			}
			if (conhecido.scale() > casas && conhecido.setScale(casas, RoundingMode.HALF_UP).compareTo(escrito) == 0) {
				return true;
			}
		}
		return false;
	}

}
