package dev.aurora.comum;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * CSV para Excel em português: separador ";", UTF-8 com BOM e quebras CRLF (RFC 4180).
 * Protege contra CSV injection (OWASP): células que começam com =, +, -, @, tab ou CR
 * recebem um apóstrofo e não viram fórmula.
 */
public final class EscritorCsv implements AutoCloseable {

	/** Número puro (inclusive negativo, com vírgula ou ponto): não é fórmula. */
	private static final Pattern NUMERO = Pattern.compile("-?\\d+([.,]\\d+)*");

	private static final char SEPARADOR = ';';

	private final Writer saida;

	public EscritorCsv(OutputStream destino) {
		this.saida = new OutputStreamWriter(destino, StandardCharsets.UTF_8);
		escrever("﻿");
	}

	public void linha(@Nullable Object... celulas) {
		var linha = new StringBuilder();
		for (int i = 0; i < celulas.length; i++) {
			if (i > 0) {
				linha.append(SEPARADOR);
			}
			linha.append(celula(celulas[i]));
		}
		escrever(linha.append("\r\n").toString());
	}

	static String celula(@Nullable Object valor) {
		if (valor == null) {
			return "";
		}
		var texto = valor.toString();
		if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0 && !(valor instanceof Number)
				&& !NUMERO.matcher(texto).matches()) {
			texto = "'" + texto;
		}
		if (texto.indexOf(SEPARADOR) >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0
				|| texto.indexOf('\r') >= 0) {
			texto = '"' + texto.replace("\"", "\"\"") + '"';
		}
		return texto;
	}

	private void escrever(String texto) {
		try {
			saida.write(texto);
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
	}

	@Override
	public void close() {
		try {
			saida.flush();
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
	}

}
