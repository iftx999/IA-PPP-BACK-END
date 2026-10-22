package dev.aurora.integracoes.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.integracoes.model.RegistroCarga;

/**
 * CSV da carga por arquivo: separador ";" ou ",", UTF-8 (com ou sem BOM), cabeçalho com
 * as colunas matricula, unidade e situacao (em qualquer ordem; aceita alguns sinônimos).
 */
final class LeitorCsvCarga {

	static final int MAXIMO_DE_LINHAS = 500_000;

	private LeitorCsvCarga() {
	}

	static List<RegistroCarga> ler(InputStream entrada) throws IOException {
		try (var leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			var cabecalho = leitor.readLine();
			if (cabecalho == null || cabecalho.isBlank()) {
				throw new RegraVioladaException("O arquivo está vazio.");
			}
			cabecalho = cabecalho.replace("﻿", "");
			char separador = cabecalho.contains(";") ? ';' : ',';
			var colunas = dividir(cabecalho, separador).stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
			int matricula = coluna(colunas, "matricula", "codigo_origem", "codigo");
			int unidade = coluna(colunas, "unidade", "setor", "codigo_unidade");
			int situacao = coluna(colunas, "situacao", "status");
			var registros = new ArrayList<RegistroCarga>();
			String linha;
			int numero = 1;
			while ((linha = leitor.readLine()) != null) {
				numero++;
				if (linha.isBlank()) {
					continue;
				}
				if (registros.size() >= MAXIMO_DE_LINHAS) {
					throw new RegraVioladaException(
							"O arquivo passa de " + MAXIMO_DE_LINHAS + " linhas; divida a carga.");
				}
				var campos = dividir(linha, separador);
				registros.add(new RegistroCarga(numero, campo(campos, matricula), campo(campos, unidade),
						campo(campos, situacao)));
			}
			return registros;
		}
	}

	private static int coluna(List<String> colunas, String... nomes) {
		for (var nome : nomes) {
			var i = colunas.indexOf(nome);
			if (i >= 0) {
				return i;
			}
		}
		throw new RegraVioladaException(
				"Falta a coluna \"" + nomes[0] + "\" no cabeçalho (esperado: matricula;unidade;situacao).");
	}

	/** Divide respeitando aspas ("a;b" é um campo só; "" vira "). */
	static List<String> dividir(String linha, char separador) {
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

	private static String campo(List<String> campos, int indice) {
		return indice < campos.size() ? campos.get(indice) : "";
	}

}
