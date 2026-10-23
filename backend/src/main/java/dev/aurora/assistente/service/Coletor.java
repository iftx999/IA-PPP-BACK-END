package dev.aurora.assistente.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.aurora.assistente.model.Fonte;
import dev.aurora.assistente.model.LinhaDeDados;

/**
 * Fontes, linhas de dados e saídas das ferramentas de uma pergunta. O número da fonte é
 * dado aqui (pelo servidor), na ordem em que os dados foram lidos; o modelo só o cita.
 */
final class Coletor {

	private final List<Fonte> fontes = new ArrayList<>();

	private final List<LinhaDeDados> dados = new ArrayList<>();

	private final List<String> saidas = new ArrayList<>();

	int registrar(String titulo, String detalhe, String tituloDosDados, Map<String, String> campos) {
		var numero = fontes.size() + 1;
		fontes.add(new Fonte(numero, titulo, detalhe));
		dados.add(new LinhaDeDados(numero, tituloDosDados, campos));
		return numero;
	}

	void guardarSaida(String json) {
		saidas.add(json);
	}

	List<Fonte> fontes() {
		return List.copyOf(fontes);
	}

	List<LinhaDeDados> dados() {
		return List.copyOf(dados);
	}

	/**
	 * Tudo o que as ferramentas devolveram ao modelo (base da verificação dos números).
	 */
	List<String> saidas() {
		return List.copyOf(saidas);
	}

	/** Fontes citadas no texto ([n]) que existem; as demais não aparecem. */
	List<Fonte> fontesCitadas(String texto) {
		return fontes.stream().filter(f -> texto.contains("[" + f.numero() + "]")).toList();
	}

}
