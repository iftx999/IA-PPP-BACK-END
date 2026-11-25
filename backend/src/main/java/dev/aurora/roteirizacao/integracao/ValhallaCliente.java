package dev.aurora.roteirizacao.integracao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.PerfilRota;
import tools.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Chamadas HTTP ao Valhalla (ADR 0046): matriz ({@code /sources_to_targets}) e rota
 * ({@code /route}). Só coordenadas saem da Aurora, e só para o contêiner do próprio
 * ambiente.
 */
class ValhallaCliente {

	/** Pares por pedido de matriz (service_limits.*.max_matrix_location_pairs padrão). */
	static final int LADO_DO_BLOCO = 50;

	/** Paradas por pedido de rota (service_limits.*.max_locations padrão). */
	static final int PARADAS_POR_ROTA = 20;

	/** Raio de busca da via mais próxima, em metros. */
	static final int RAIO_M = 150;

	/**
	 * Só encaixa o ponto em via ligada a pelo menos este número de outras: evita trechos
	 * isolados (condomínio, estacionamento) sem saída na malha.
	 */
	static final int ALCANCE_MINIMO = 50;

	private final RestClient http;

	ValhallaCliente(RestClient http) {
		this.http = http;
	}

	/**
	 * Tempos (s) e distâncias (m) de cada origem para cada destino; -1 quando não há
	 * caminho.
	 */
	int[][][] matriz(List<Coordenada> origens, List<Coordenada> destinos, PerfilRota perfil) {
		var corpo = new LinkedHashMap<String, Object>();
		corpo.put("sources", locais(origens));
		corpo.put("targets", locais(destinos));
		corpo.put("costing", custo(perfil));
		corpo.put("units", "kilometers");
		var resposta = http.post()
			.uri("/sources_to_targets")
			.contentType(MediaType.APPLICATION_JSON)
			.body(corpo)
			.retrieve()
			.body(JsonNode.class);
		var linhas = resposta == null ? null : resposta.get("sources_to_targets");
		if (linhas == null || linhas.size() != origens.size()) {
			throw new IllegalStateException("resposta de matriz inesperada do Valhalla");
		}
		var segundos = new int[origens.size()][destinos.size()];
		var metros = new int[origens.size()][destinos.size()];
		for (int i = 0; i < origens.size(); i++) {
			var linha = linhas.get(i);
			for (int j = 0; j < destinos.size(); j++) {
				var celula = linha.get(j);
				var tempo = celula == null ? null : celula.get("time");
				var distancia = celula == null ? null : celula.get("distance");
				boolean semCaminho = tempo == null || tempo.isNull() || distancia == null || distancia.isNull();
				segundos[i][j] = semCaminho ? -1 : (int) Math.round(tempo.asDouble());
				metros[i][j] = semCaminho ? -1 : (int) Math.round(distancia.asDouble() * 1000);
			}
		}
		return new int[][][] { segundos, metros };
	}

	/** Traçado pelas paradas (até {@link #PARADAS_POR_ROTA}), um trecho por perna. */
	List<List<Coordenada>> rota(List<Coordenada> paradas, PerfilRota perfil) {
		var corpo = new LinkedHashMap<String, Object>();
		corpo.put("locations", locais(paradas));
		corpo.put("costing", custo(perfil));
		corpo.put("directions_type", "none");
		var resposta = http.post()
			.uri("/route")
			.contentType(MediaType.APPLICATION_JSON)
			.body(corpo)
			.retrieve()
			.body(JsonNode.class);
		var pernas = resposta == null ? null : resposta.path("trip").get("legs");
		if (pernas == null) {
			throw new IllegalStateException("resposta de rota inesperada do Valhalla");
		}
		var linhas = new ArrayList<List<Coordenada>>();
		for (var perna : pernas) {
			linhas.add(Polyline6.decodificar(perna.get("shape").asString()));
		}
		return linhas;
	}

	static String custo(PerfilRota perfil) {
		return switch (perfil) {
			case MOTO -> "motorcycle";
			case CARRO -> "auto";
			case CAMINHAO -> "truck";
		};
	}

	private static List<Map<String, Object>> locais(List<Coordenada> pontos) {
		return pontos.stream()
			.<Map<String, Object>>map(p -> Map.of("lat", p.latitude(), "lon", p.longitude(), "radius", RAIO_M,
					"minimum_reachability", ALCANCE_MINIMO))
			.toList();
	}

}
