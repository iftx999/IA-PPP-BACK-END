package dev.aurora.comum;

import java.util.ArrayList;
import java.util.List;

/**
 * Divisão de uma linha de CSV respeitando aspas, para os leitores de arquivo dos módulos.
 */
public final class LinhaCsv {

	private LinhaCsv() {
	}

	/**
	 * Divide respeitando aspas ("a;b" é um campo só; "" vira "); campos sem espaços nas
	 * pontas.
	 */
	public static List<String> dividir(String linha, char separador) {
		var campos = new ArrayList<String>();
		var atual = new StringBuilder();
		boolean entreAspas = false;
		for (int i = 0; i < linha.length(); i++) {
			char c = linha.charAt(i);
			if (c == '"') {
				if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
					atual.append('"');
					i++;
				}
				else {
					entreAspas = !entreAspas;
				}
			}
			else if (c == separador && !entreAspas) {
				campos.add(atual.toString().strip());
				atual.setLength(0);
			}
			else {
				atual.append(c);
			}
		}
		campos.add(atual.toString().strip());
		return campos;
	}

}
