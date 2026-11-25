package dev.aurora.logradouros.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.logradouros.model.FeicaoTrecho;
import dev.aurora.logradouros.model.Lado;
import dev.aurora.logradouros.model.Leitura;
import dev.aurora.logradouros.model.Rejeicao;
import dev.aurora.logradouros.model.Textos;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trechos em GeoJSON (ADR 0043): FeatureCollection de LineString ou MultiLineString em
 * graus (EPSG:4326, igual ao SIRGAS 2000 para o campo), uma feição por trecho, com as
 * propriedades codigo_logradouro, nome, codigo_trecho e, opcionais, tipo, bairro,
 * numero_inicial, numero_final e lado. Lido feição a feição, sem montar o arquivo inteiro
 * na memória.
 */
final class LeitorGeoJsonTrechos {

	static final int MAXIMO_DE_FEICOES = 200_000;

	/**
	 * Retângulo do Brasil com folga: pega latitude e longitude trocadas e coordenadas em
	 * metros (UTM).
	 */
	private static final double OESTE = -75, LESTE = -28, SUL = -35, NORTE = 7;

	private LeitorGeoJsonTrechos() {
	}

	static Leitura<FeicaoTrecho> ler(InputStream entrada, JsonMapper json) throws IOException {
		return ler(entrada, json, MAXIMO_DE_FEICOES);
	}

	/** Com o limite de feições informado (os testes usam um pequeno). */
	static Leitura<FeicaoTrecho> ler(InputStream entrada, JsonMapper json, int maximo) throws IOException {
		var itens = new ArrayList<FeicaoTrecho>();
		var rejeicoes = new ArrayList<Rejeicao>();
		var codigos = new HashSet<String>();
		int posicao = 0;
		try (JsonParser parser = json.createParser(entrada)) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				throw new RegraVioladaException("O arquivo não é um GeoJSON (esperado um objeto FeatureCollection).");
			}
			boolean achouFeicoes = false;
			while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
				var nome = parser.currentName();
				parser.nextToken();
				if ("type".equals(nome) && !"FeatureCollection".equals(parser.getString())) {
					throw new RegraVioladaException("O GeoJSON deve ser uma FeatureCollection.");
				}
				if (!"features".equals(nome)) {
					parser.skipChildren();
					continue;
				}
				if (parser.currentToken() != JsonToken.START_ARRAY) {
					throw new RegraVioladaException("\"features\" deve ser uma lista.");
				}
				achouFeicoes = true;
				while (parser.nextToken() == JsonToken.START_OBJECT) {
					posicao++;
					if (posicao > maximo) {
						throw new RegraVioladaException(
								"O arquivo passa de " + maximo + " trechos; divida por município.");
					}
					JsonNode feicao = parser.readValueAsTree();
					try {
						var trecho = feicao(posicao, feicao);
						if (!codigos.add(trecho.codigoTrecho())) {
							throw new RegraVioladaException(
									"codigo_trecho repetido no arquivo: " + trecho.codigoTrecho());
						}
						itens.add(trecho);
					}
					catch (RegraVioladaException e) {
						rejeicoes.add(new Rejeicao(posicao, e.getMessage()));
					}
				}
			}
			if (!achouFeicoes) {
				throw new RegraVioladaException("O GeoJSON não tem \"features\".");
			}
		}
		catch (tools.jackson.core.JacksonException e) {
			throw new RegraVioladaException("O arquivo não é um JSON válido (perto da feição " + (posicao + 1) + ").");
		}
		if (posicao == 0) {
			throw new RegraVioladaException("O arquivo não tem trechos.");
		}
		return new Leitura<>(posicao, itens, rejeicoes);
	}

	private static FeicaoTrecho feicao(int posicao, JsonNode feicao) {
		var geometria = feicao.get("geometry");
		if (geometria == null || !geometria.isObject()) {
			throw new RegraVioladaException("Feição sem geometria.");
		}
		var tipo = texto(geometria, "type");
		if (!"LineString".equals(tipo) && !"MultiLineString".equals(tipo)) {
			throw new RegraVioladaException("Geometria " + tipo + ": o trecho deve ser LineString ou MultiLineString.");
		}
		var coordenadas = geometria.get("coordinates");
		if (coordenadas == null || !coordenadas.isArray() || coordenadas.isEmpty()) {
			throw new RegraVioladaException("Geometria sem coordenadas.");
		}
		if ("LineString".equals(tipo)) {
			linha(coordenadas);
		}
		else {
			for (var parte : coordenadas) {
				linha(parte);
			}
		}
		var p = feicao.get("properties");
		if (p == null || !p.isObject()) {
			throw new RegraVioladaException("Feição sem propriedades.");
		}
		var codigoLogradouro = obrigatorio(p, "codigo_logradouro", 40);
		var nome = obrigatorio(p, "nome", 120);
		var codigoTrecho = obrigatorio(p, "codigo_trecho", 40);
		var inicial = inteiro(p, "numero_inicial");
		var fim = inteiro(p, "numero_final");
		if (inicial != null && fim != null && inicial > fim) {
			throw new RegraVioladaException("numero_inicial maior que numero_final.");
		}
		return new FeicaoTrecho(posicao, codigoLogradouro, Textos.opcional(texto(p, "tipo"), 30, "tipo"), nome,
				Textos.opcional(texto(p, "bairro"), 80, "bairro"), codigoTrecho, inicial, fim,
				Lado.de(texto(p, "lado")), geometria.toString());
	}

	private static void linha(JsonNode pontos) {
		if (!pontos.isArray() || pontos.size() < 2) {
			throw new RegraVioladaException("Linha com menos de 2 pontos.");
		}
		for (var ponto : pontos) {
			if (!ponto.isArray() || ponto.size() < 2 || !ponto.get(0).isNumber() || !ponto.get(1).isNumber()) {
				throw new RegraVioladaException("Coordenada inválida: " + ponto);
			}
			double lon = ponto.get(0).asDouble(), lat = ponto.get(1).asDouble();
			if (lon < OESTE || lon > LESTE || lat < SUL || lat > NORTE) {
				throw new RegraVioladaException("Coordenada fora do Brasil (" + lon + ", " + lat
						+ "): o arquivo deve estar em graus, longitude antes da latitude (EPSG:4326 ou 4674).");
			}
		}
	}

	private static String obrigatorio(JsonNode p, String campo, int maximo) {
		var valor = Textos.opcional(texto(p, campo), maximo, campo);
		if (valor == null) {
			throw new RegraVioladaException("Falta a propriedade " + campo + ".");
		}
		return valor;
	}

	private static @Nullable String texto(JsonNode no, String campo) {
		var valor = no.get(campo);
		return valor == null || valor.isNull() ? null : valor.isValueNode() ? valor.asString() : null;
	}

	private static @Nullable Integer inteiro(JsonNode p, String campo) {
		var valor = texto(p, campo);
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			var numero = Integer.parseInt(valor.strip().replaceAll("\\.0+$", ""));
			if (numero < 0) {
				throw new NumberFormatException();
			}
			return numero;
		}
		catch (NumberFormatException e) {
			throw new RegraVioladaException(campo + " inválido: \"" + valor + "\".");
		}
	}

}
