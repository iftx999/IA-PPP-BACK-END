package dev.aurora.logradouros.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import dev.aurora.comum.LinhaCsv;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.logradouros.model.Leitura;
import dev.aurora.logradouros.model.LinhaEndereco;
import dev.aurora.logradouros.model.Numero;
import dev.aurora.logradouros.model.Rejeicao;
import dev.aurora.logradouros.model.Textos;

/**
 * Endereços dos imóveis em CSV (ADR 0043):
 * matricula;codigo_logradouro;numero;complemento, separado por ";" ou ",", UTF-8, com
 * cabeçalho. Complemento é opcional; número pode ser "S/N".
 */
final class LeitorCsvEnderecos {

	static final int MAXIMO_DE_LINHAS = 1_000_000;

	private LeitorCsvEnderecos() {
	}

	static Leitura<LinhaEndereco> ler(InputStream entrada) throws IOException {
		return ler(entrada, MAXIMO_DE_LINHAS);
	}

	/** Com o limite de linhas informado (os testes usam um pequeno). */
	static Leitura<LinhaEndereco> ler(InputStream entrada, int maximo) throws IOException {
		try (var leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			var cabecalho = leitor.readLine();
			if (cabecalho == null || cabecalho.isBlank()) {
				throw new RegraVioladaException("O arquivo está vazio.");
			}
			cabecalho = cabecalho.replace("﻿", "");
			char separador = cabecalho.contains(";") ? ';' : ',';
			var colunas = LinhaCsv.dividir(cabecalho, separador).stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
			int matricula = coluna(colunas, true, "matricula", "codigo_origem");
			int logradouro = coluna(colunas, true, "codigo_logradouro", "logradouro");
			int numero = coluna(colunas, true, "numero");
			int complemento = coluna(colunas, false, "complemento");
			var itens = new ArrayList<LinhaEndereco>();
			var rejeicoes = new ArrayList<Rejeicao>();
			var matriculas = new HashSet<String>();
			String texto;
			int linha = 1;
			int lidos = 0;
			while ((texto = leitor.readLine()) != null) {
				linha++;
				if (texto.isBlank()) {
					continue;
				}
				if (++lidos > maximo) {
					throw new RegraVioladaException("O arquivo passa de " + maximo + " linhas; divida o envio.");
				}
				var campos = LinhaCsv.dividir(texto, separador);
				try {
					var m = campo(campos, matricula);
					if (m.isEmpty() || m.length() > 40) {
						throw new RegraVioladaException("Matrícula vazia ou com mais de 40 caracteres.");
					}
					if (!matriculas.add(m)) {
						throw new RegraVioladaException("Matrícula repetida no arquivo.");
					}
					var codigo = campo(campos, logradouro);
					if (codigo.isEmpty() || codigo.length() > 40) {
						throw new RegraVioladaException("codigo_logradouro vazio ou com mais de 40 caracteres.");
					}
					var n = Textos.opcional(campo(campos, numero), 10, "numero");
					itens.add(new LinhaEndereco(linha, m, codigo, n, Numero.inteiro(n),
							complemento < 0 ? null : Textos.opcional(campo(campos, complemento), 60, "complemento")));
				}
				catch (RegraVioladaException e) {
					rejeicoes.add(new Rejeicao(linha, e.getMessage()));
				}
			}
			if (lidos == 0) {
				throw new RegraVioladaException("O arquivo não tem linhas depois do cabeçalho.");
			}
			return new Leitura<>(lidos, itens, rejeicoes);
		}
	}

	private static int coluna(List<String> colunas, boolean obrigatoria, String... nomes) {
		for (var nome : nomes) {
			var i = colunas.indexOf(nome);
			if (i >= 0) {
				return i;
			}
		}
		if (obrigatoria) {
			throw new RegraVioladaException("Falta a coluna \"" + nomes[0]
					+ "\" no cabeçalho (esperado: matricula;codigo_logradouro;numero;complemento).");
		}
		return -1;
	}

	private static String campo(List<String> campos, int indice) {
		return indice < campos.size() ? campos.get(indice) : "";
	}

}
