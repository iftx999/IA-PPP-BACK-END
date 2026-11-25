package dev.aurora.logradouros.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.logradouros.model.DadosLogradouro;
import dev.aurora.logradouros.model.DadosTrecho;
import dev.aurora.logradouros.model.ImovelNoLogradouro;
import dev.aurora.logradouros.model.Lado;
import dev.aurora.logradouros.model.Textos;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0043: limites exatos dos leitores e das correções manuais. */
class FronteirasDosLeitoresTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void coordenadasNasBordasDoBrasil() throws Exception {
		assertThat(motivo(linha("[[-75,-35],[-28,7]]"))).isNull();
		for (var fora : new String[] { "[[-75.01,-20],[-47,-20]]", "[[-47,-20],[-27.99,-20]]",
				"[[-47,-35.01],[-47,-20]]", "[[-47,-20],[-47,7.01]]" }) {
			assertThat(motivo(linha(fora))).as(fora).startsWith("Coordenada fora do Brasil");
		}
		assertThat(motivo(linha("[[-47,-20]]"))).isEqualTo("Linha com menos de 2 pontos.");
		assertThat(motivo(linha("[[-47,-20],[-47]]"))).startsWith("Coordenada inválida");
		assertThat(motivo(linha("[[-47,-20],[\"-47\",-20]]"))).startsWith("Coordenada inválida");
		assertThat(motivo(linha("[[-47,-20],[-47,\"-20\"]]"))).startsWith("Coordenada inválida");
		assertThat(motivo(linha("[[-47,-20],5]"))).startsWith("Coordenada inválida");
		assertThat(motivo(linha("[]"))).isEqualTo("Geometria sem coordenadas.");
		assertThat(motivo(linha("5"))).isEqualTo("Geometria sem coordenadas.");
		assertThat(motivo(feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"}",
				"{\"type\":\"LineString\"}")))
			.isEqualTo("Geometria sem coordenadas.");
		assertThat(motivo(feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"}",
				"{\"type\":\"MultiLineString\",\"coordinates\":[[[-47,-20],[-46,-20]],[[-47,-20]]]}")))
			.isEqualTo("Linha com menos de 2 pontos.");
		assertThat(motivo(feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"}", "null")))
			.isEqualTo("Feição sem geometria.");
		assertThat(motivo(feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"}", "[1]")))
			.isEqualTo("Feição sem geometria.");
		assertThat(motivo(feicao("null", "{\"type\":\"LineString\",\"coordinates\":[[-47,-20],[-46,-20]]}")))
			.isEqualTo("Feição sem propriedades.");
		assertThat(motivo(feicao("[]", "{\"type\":\"LineString\",\"coordinates\":[[-47,-20],[-46,-20]]}")))
			.isEqualTo("Feição sem propriedades.");
	}

	@Test
	void numeracaoDoTrecho() throws Exception {
		assertThat(trecho("\"numero_inicial\":10,\"numero_final\":10").numeroInicial()).isEqualTo(10);
		assertThat(trecho("\"numero_inicial\":\"0\",\"numero_final\":\"7.0\"").numeroFinal()).isEqualTo(7);
		assertThat(trecho("\"numero_inicial\":\" \"").numeroInicial()).isNull();
		assertThat(trecho("\"numero_inicial\":null,\"lado\":\"PAR\"").lado()).isEqualTo(Lado.PAR);
		assertThat(trecho("\"numero_inicial\":{\"x\":1}").numeroInicial()).isNull();
		assertThat(motivo(trecho2("\"numero_inicial\":-1"))).isEqualTo("numero_inicial inválido: \"-1\".");
		assertThat(motivo(trecho2("\"numero_final\":\"abc\""))).isEqualTo("numero_final inválido: \"abc\".");
		assertThat(motivo(trecho2("\"numero_inicial\":11,\"numero_final\":10")))
			.isEqualTo("numero_inicial maior que numero_final.");
		assertThat(motivo(
				feicao("{\"codigo_logradouro\":\"" + "x".repeat(41) + "\",\"nome\":\"R\",\"codigo_trecho\":\"T\"}",
						"{\"type\":\"LineString\",\"coordinates\":[[-47,-20],[-46,-20]]}")))
			.contains("codigo_logradouro");
	}

	@Test
	void estruturaELimiteDoGeoJson() throws Exception {
		var dois = colecao(2);
		assertThat(LeitorGeoJsonTrechos.ler(entrada(dois), JSON, 2).itens()).hasSize(2);
		assertThatThrownBy(() -> LeitorGeoJsonTrechos.ler(entrada(colecao(3)), JSON, 2))
			.hasMessageContaining("passa de 2 trechos");
		assertThatThrownBy(
				() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"FeatureCollection\",\"features\":{}}"), JSON))
			.hasMessageContaining("deve ser uma lista");
		assertThatThrownBy(() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"Topology\",\"features\":[]}"), JSON))
			.hasMessageContaining("FeatureCollection");
		// "features" antes de "type" e propriedades desconhecidas são aceitas.
		var invertido = "{\"features\":"
				+ colecao(1).substring(colecao(1).indexOf('['), colecao(1).lastIndexOf(']') + 1)
				+ ",\"crs\":{\"type\":\"name\"},\"type\":\"FeatureCollection\"}";
		assertThat(LeitorGeoJsonTrechos.ler(entrada(invertido), JSON).lidos()).isEqualTo(1);
		assertThatThrownBy(
				() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"FeatureCollection\",\"features\":[{]}"), JSON))
			.hasMessageContaining("perto da feição 2");
	}

	@Test
	void csvBordasELimite() throws Exception {
		var leitura = LeitorCsvEnderecos.ler(entrada("""
				numero,complemento,codigo_origem,logradouro
				1,,%s,%s
				2,c,%s,%s
				""".formatted("m".repeat(40), "l".repeat(40), "n".repeat(41), "L")));
		assertThat(leitura.itens()).hasSize(1);
		assertThat(leitura.itens().getFirst().matricula()).hasSize(40);
		assertThat(leitura.itens().getFirst().codigoLogradouro()).hasSize(40);
		assertThat(leitura.itens().getFirst().numero()).isEqualTo("1");
		assertThat(leitura.itens().getFirst().complemento()).isNull();
		assertThat(leitura.rejeicoes().getFirst().posicao()).isEqualTo(3);
		var codigoLongo = LeitorCsvEnderecos
			.ler(entrada("matricula;codigo_logradouro;numero\nm;" + "l".repeat(41) + ";1\n"));
		assertThat(codigoLongo.rejeicoes()).hasSize(1);
		assertThat(LeitorCsvEnderecos.ler(entrada("matricula;codigo_logradouro;numero\na;1;1\nb;1;2\n"), 2).itens())
			.hasSize(2);
		assertThatThrownBy(
				() -> LeitorCsvEnderecos.ler(entrada("matricula;codigo_logradouro;numero\na;1;1\nb;1;2\nc;1;3\n"), 2))
			.hasMessageContaining("passa de 2 linhas");
		assertThatThrownBy(() -> LeitorCsvEnderecos.ler(entrada("   \n"))).hasMessageContaining("vazio");
		// Linha curta: campos que faltam ficam vazios.
		var curta = LeitorCsvEnderecos.ler(entrada("matricula;codigo_logradouro;numero;complemento\na;1\n"));
		assertThat(curta.itens().getFirst().numero()).isNull();
		assertThat(LeitorCsvEnderecos.ler(entrada("﻿matricula;codigo_logradouro;numero\na;1;1\n")).itens()).hasSize(1);
	}

	@Test
	void correcoesManuais() {
		new DadosTrecho(0, 0, Lado.AMBOS).validar();
		new DadosTrecho(null, null, Lado.PAR).validar();
		new DadosTrecho(5, null, Lado.PAR).validar();
		new DadosTrecho(null, 5, Lado.PAR).validar();
		assertThatThrownBy(() -> new DadosTrecho(-1, 5, Lado.PAR).validar()).hasMessageContaining("negativa");
		assertThatThrownBy(() -> new DadosTrecho(1, -1, Lado.PAR).validar()).hasMessageContaining("negativa");
		assertThatThrownBy(() -> new DadosTrecho(6, 5, Lado.PAR).validar()).hasMessageContaining("maior que o final");

		assertThat(new DadosLogradouro(" Rua ", "  São   João ", "").normalizados())
			.isEqualTo(new DadosLogradouro("Rua", "São João", null));
		assertThat(new DadosLogradouro(null, "x".repeat(120), null).normalizados().nome()).hasSize(120);
		assertThatThrownBy(() -> new DadosLogradouro(null, "x".repeat(121), null).normalizados())
			.isInstanceOf(RegraVioladaException.class);
		assertThatThrownBy(() -> new DadosLogradouro(null, "  ", null).normalizados())
			.isInstanceOf(RegraVioladaException.class);
		assertThat(Textos.opcional("x".repeat(30), 30, "Tipo")).hasSize(30);
		assertThatThrownBy(() -> Textos.opcional("x".repeat(31), 30, "Tipo")).hasMessageContaining("Tipo");
		assertThat(Textos.paraBusca("  Avenida  São   JOÃO ")).isEqualTo("avenida sao joao");
		assertThat(new ImovelNoLogradouro("123", "1", null, "T1", "ENDERECO").comMatricula("••••123").matricula())
			.isEqualTo("••••123");
		assertThat(Lado.de(" ")).isEqualTo(Lado.AMBOS);
		assertThat(Lado.de("ambos")).isEqualTo(Lado.AMBOS);
		assertThat(Lado.de("I")).isEqualTo(Lado.IMPAR);
	}

	private static String linha(String coordenadas) {
		return feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"}",
				"{\"type\":\"LineString\",\"coordinates\":" + coordenadas + "}");
	}

	private static String trecho2(String propriedades) {
		return feicao("{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T\"," + propriedades + "}",
				"{\"type\":\"LineString\",\"coordinates\":[[-47,-20],[-46,-20]]}");
	}

	private static dev.aurora.logradouros.model.FeicaoTrecho trecho(String propriedades) throws Exception {
		var leitura = LeitorGeoJsonTrechos.ler(entrada(trecho2(propriedades)), JSON);
		assertThat(leitura.rejeicoes()).isEmpty();
		return leitura.itens().getFirst();
	}

	private static String feicao(String propriedades, String geometria) {
		return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":" + propriedades
				+ ",\"geometry\":" + geometria + "}]}";
	}

	private static String colecao(int quantidade) {
		var feicoes = new StringBuilder();
		for (int i = 1; i <= quantidade; i++) {
			feicoes.append(i > 1 ? "," : "")
				.append("{\"type\":\"Feature\",\"properties\":{\"codigo_logradouro\":1,\"nome\":\"R\",\"codigo_trecho\":\"T")
				.append(i)
				.append("\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-47,-20],[-46,-20]]}}");
		}
		return "{\"type\":\"FeatureCollection\",\"features\":[" + feicoes + "]}";
	}

	private static String motivo(String geojson) throws Exception {
		var leitura = LeitorGeoJsonTrechos.ler(entrada(geojson), JSON);
		return leitura.rejeicoes().isEmpty() ? null : leitura.rejeicoes().getFirst().motivo();
	}

	private static ByteArrayInputStream entrada(String texto) {
		return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
	}

}
