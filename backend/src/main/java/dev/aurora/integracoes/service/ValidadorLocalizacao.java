package dev.aurora.integracoes.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.aurora.integracoes.model.Rejeicao;
import dev.aurora.integracoes.model.RegistroLocalizacao;

/**
 * Validação das localizações linha a linha, como a das cargas: o que está errado é
 * recusado com motivo. O ponto precisa cair no entorno do Brasil (SIRGAS 2000, lon/lat).
 */
final class ValidadorLocalizacao {

	private static final Pattern MATRICULA = Pattern.compile("[\\p{Alnum}./-]{1,40}");

	private static final Set<String> PRECISOES = Set.of("ENDERECO", "LOGRADOURO", "BAIRRO");

	record Valida(String matricula, UUID unidade, double longitude, double latitude, String precisao) {
	}

	record Resultado(List<Valida> validas, long rejeitados, List<Rejeicao> rejeicoes) {
	}

	private ValidadorLocalizacao() {
	}

	static Resultado validar(List<RegistroLocalizacao> registros, Map<String, UUID> unidades) {
		var validas = new ArrayList<Valida>();
		var rejeicoes = new ArrayList<Rejeicao>();
		var vistas = new HashSet<String>();
		long rejeitados = 0;
		for (var r : registros) {
			var matricula = r.matricula() == null ? "" : r.matricula().strip();
			var precisao = r.precisao() == null ? "" : r.precisao().strip().toUpperCase(Locale.ROOT);
			String motivo = null;
			UUID unidade = null;
			if (!MATRICULA.matcher(matricula).matches()) {
				motivo = "Matrícula vazia ou com caracteres inválidos.";
			}
			else if (!vistas.add(matricula)) {
				motivo = "Matrícula repetida neste envio.";
			}
			else if ((unidade = unidades.get(ValidadorCarga.codigo(r.unidade()))) == null) {
				motivo = "Unidade desconhecida ou inativa.";
			}
			else if (!noBrasil(r.longitude(), r.latitude())) {
				motivo = "Coordenadas ausentes ou fora do Brasil (use longitude e latitude em graus, SIRGAS 2000).";
			}
			else if (!PRECISOES.contains(precisao)) {
				motivo = "Precisão inválida (use ENDERECO, LOGRADOURO ou BAIRRO).";
			}
			if (motivo == null) {
				validas.add(new Valida(matricula, unidade, r.longitude(), r.latitude(), precisao));
			}
			else {
				rejeitados++;
				if (rejeicoes.size() < ValidadorCarga.REJEICOES_DETALHADAS) {
					rejeicoes.add(new Rejeicao(r.linha(),
							matricula.isEmpty() || matricula.length() > 40 ? null : matricula, motivo));
				}
			}
		}
		return new Resultado(validas, rejeitados, rejeicoes);
	}

	private static boolean noBrasil(Double longitude, Double latitude) {
		return longitude != null && latitude != null && Double.isFinite(longitude) && Double.isFinite(latitude)
				&& longitude >= -75 && longitude <= -28 && latitude >= -35 && latitude <= 7;
	}

}
