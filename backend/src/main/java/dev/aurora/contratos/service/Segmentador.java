package dev.aurora.contratos.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.model.Trecho;
import org.jspecify.annotations.Nullable;

/**
 * Divide o texto do contrato em trechos por cláusula, mantendo a página (doc 05, 5.3,
 * passo 3). Reconhece numeração no início da linha ("7.2", "7.2.1 -", "CLÁUSULA 7") e o
 * cabeçalho por extenso ("CLÁUSULA SÉTIMA"). Texto antes da primeira cláusula vira um
 * trecho sem número.
 */
final class Segmentador {

	private static final Pattern NUMERADA = Pattern.compile(
			"^\\s*(?:CL[ÁA]USULA\\s+)?(\\d{1,3}(?:\\.\\d{1,3}){0,4})\\s*[.)\\-–—:]?\\s+\\S",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern POR_EXTENSO = Pattern.compile("^\\s*CL[ÁA]USULA\\s+([\\p{L}]+(?:\\s+[\\p{L}]+)?)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/** Trechos acima disso são quebrados, para nenhum segmento estourar o pedido à IA. */
	static final int TAMANHO_MAXIMO = 6_000;

	private Segmentador() {
	}

	static List<Trecho> segmentar(List<Pagina> paginas) {
		var trechos = new ArrayList<Trecho>();
		String clausula = null;
		int paginaInicio = 1;
		var texto = new StringBuilder();
		for (var pagina : paginas) {
			for (var linha : pagina.texto().split("\\R")) {
				var nova = clausulaDaLinha(linha);
				if (nova != null) {
					fechar(trechos, clausula, paginaInicio, texto);
					clausula = nova;
					paginaInicio = pagina.numero();
				}
				else if (texto.isEmpty()) {
					paginaInicio = pagina.numero();
				}
				if (texto.length() >= TAMANHO_MAXIMO) {
					fechar(trechos, clausula, paginaInicio, texto);
					paginaInicio = pagina.numero();
				}
				texto.append(linha).append('\n');
			}
		}
		fechar(trechos, clausula, paginaInicio, texto);
		return trechos;
	}

	static @Nullable String clausulaDaLinha(String linha) {
		var numerada = NUMERADA.matcher(linha);
		if (numerada.find()) {
			return numerada.group(1);
		}
		var extenso = POR_EXTENSO.matcher(linha);
		return extenso.find() ? extenso.group(1).toUpperCase(java.util.Locale.ROOT) : null;
	}

	private static void fechar(List<Trecho> trechos, @Nullable String clausula, int pagina, StringBuilder texto) {
		var conteudo = texto.toString().strip();
		if (!conteudo.isEmpty()) {
			trechos.add(new Trecho(clausula, pagina, conteudo));
		}
		texto.setLength(0);
	}

}
