package dev.aurora.integracoes.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.integracoes.model.RegistroCarga;
import dev.aurora.integracoes.model.Rejeicao;

/**
 * Validação linha a linha: o que está errado é recusado com motivo, sem derrubar a carga.
 * Aceita a situação por código ou por rótulo ("Factível", "Sem rede").
 */
final class ValidadorCarga {

	private static final Pattern MATRICULA = Pattern.compile("[\\p{Alnum}./-]{1,40}");

	private static final Map<String, String> SITUACOES = Map.of("LIGADO", "LIGADO", "FACTIVEL", "FACTIVEL",
			"FACTIVEL SEM LIGACAO", "FACTIVEL", "CORTADO", "CORTADO", "POTENCIAL", "POTENCIAL", "SEM REDE",
			"POTENCIAL");

	/** Quantas recusas detalhar na execução (o total é sempre contado). */
	static final int REJEICOES_DETALHADAS = 100;

	record Valido(String matricula, UUID unidade, String situacao) {
	}

	record Resultado(List<Valido> validos, long rejeitados, List<Rejeicao> rejeicoes) {
	}

	private ValidadorCarga() {
	}

	/**
	 * @param unidades código da unidade (maiúsculo) -> id, das unidades que recebem
	 * imóveis
	 */
	static Resultado validar(List<RegistroCarga> registros, Map<String, UUID> unidades) {
		var validos = new ArrayList<Valido>();
		var rejeicoes = new ArrayList<Rejeicao>();
		var vistas = new HashSet<String>();
		long rejeitados = 0;
		for (var r : registros) {
			var matricula = r.matricula() == null ? "" : r.matricula().strip();
			String motivo = null;
			UUID unidade = null;
			String situacao = null;
			if (!MATRICULA.matcher(matricula).matches()) {
				motivo = "Matrícula vazia ou com caracteres inválidos.";
			}
			else if (!vistas.add(matricula)) {
				motivo = "Matrícula repetida nesta carga.";
			}
			else if ((unidade = unidades.get(codigo(r.unidade()))) == null) {
				motivo = "Unidade desconhecida ou inativa: " + texto(r.unidade()) + ".";
			}
			else if ((situacao = SITUACOES.get(normalizar(r.situacao()))) == null) {
				motivo = "Situação inválida: " + texto(r.situacao()) + " (use LIGADO, FACTIVEL, CORTADO ou POTENCIAL).";
			}
			if (motivo == null) {
				validos.add(new Valido(matricula, unidade, situacao));
			}
			else {
				rejeitados++;
				if (rejeicoes.size() < REJEICOES_DETALHADAS) {
					rejeicoes.add(new Rejeicao(r.linha(), matricula.isEmpty() ? null : limitar(matricula), motivo));
				}
			}
		}
		return new Resultado(validos, rejeitados, rejeicoes);
	}

	static String codigo(String codigo) {
		return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
	}

	private static String normalizar(String situacao) {
		if (situacao == null) {
			return "";
		}
		var semAcento = Normalizer.normalize(situacao, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", " ");
	}

	private static String texto(String valor) {
		return valor == null || valor.isBlank() ? "(vazio)" : limitar(valor.strip());
	}

	private static String limitar(String valor) {
		return valor.length() > 40 ? valor.substring(0, 40) + "…" : valor;
	}

}
