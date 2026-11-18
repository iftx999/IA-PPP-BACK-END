package dev.aurora.logradouros.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.logradouros.model.Lado;
import dev.aurora.logradouros.model.Numero;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0043: leitura e validação dos arquivos da concessionária, item a item. */
class LeitoresDeLogradouroTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void geoJsonValidaCadaTrecho() throws Exception {
		var leitura = LeitorGeoJsonTrechos.ler(entrada("""
				{"type":"FeatureCollection","name":"ruas","features":[
				 {"type":"Feature","properties":{"codigo_logradouro":101,"nome":" Rua  São João ","codigo_trecho":"T1",
				   "numero_inicial":"1","numero_final":99,"lado":"ímpar","tipo":"Rua","bairro":"Centro"},
				  "geometry":{"type":"LineString","coordinates":[[-47.06,-22.90],[-47.05,-22.90]]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"101","nome":"Rua São João","codigo_trecho":"T1"},
				  "geometry":{"type":"LineString","coordinates":[[-47.06,-22.90],[-47.05,-22.90]]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Av. Brasil","codigo_trecho":"T3"},
				  "geometry":{"type":"LineString","coordinates":[[-22.90,-47.06],[-22.91,-47.05]]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Av. Brasil","codigo_trecho":"T4"},
				  "geometry":{"type":"Point","coordinates":[-47.06,-22.90]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"102","codigo_trecho":"T5"},
				  "geometry":{"type":"LineString","coordinates":[[-47.06,-22.90],[-47.05,-22.90]]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Av. Brasil","codigo_trecho":"T6",
				   "numero_inicial":50,"numero_final":10},
				  "geometry":{"type":"MultiLineString","coordinates":[[[-47.06,-22.90],[-47.05,-22.90]]]}},
				 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Av. Brasil","codigo_trecho":"T7",
				   "lado":"talvez"},
				  "geometry":{"type":"LineString","coordinates":[[-47.06,-22.90,700],[-47.05,-22.90,701]]}}
				]}"""), JSON);
		assertThat(leitura.lidos()).isEqualTo(7);
		assertThat(leitura.itens()).hasSize(1);
		var t1 = leitura.itens().getFirst();
		assertThat(t1.codigoLogradouro()).isEqualTo("101");
		assertThat(t1.nome()).isEqualTo("Rua São João");
		assertThat(t1.numeroInicial()).isEqualTo(1);
		assertThat(t1.numeroFinal()).isEqualTo(99);
		assertThat(t1.lado()).isEqualTo(Lado.IMPAR);
		assertThat(leitura.rejeicoes()).extracting(r -> r.posicao() + ": " + r.motivo())
			.containsExactly("2: codigo_trecho repetido no arquivo: T1",
					"3: Coordenada fora do Brasil (-22.9, -47.06): "
							+ "o arquivo deve estar em graus, longitude antes da latitude (EPSG:4326 ou 4674).",
					"4: Geometria Point: o trecho deve ser LineString ou MultiLineString.",
					"5: Falta a propriedade nome.", "6: numero_inicial maior que numero_final.",
					"7: Lado inválido: \"talvez\" (use PAR, IMPAR ou AMBOS).");
	}

	@Test
	void geoJsonQueNaoServe() {
		assertThatThrownBy(() -> LeitorGeoJsonTrechos.ler(entrada("[1,2]"), JSON))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("FeatureCollection");
		assertThatThrownBy(() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"Feature\"}"), JSON))
			.hasMessageContaining("FeatureCollection");
		assertThatThrownBy(() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"FeatureCollection\"}"), JSON))
			.hasMessageContaining("não tem \"features\"");
		assertThatThrownBy(
				() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"FeatureCollection\",\"features\":[]}"), JSON))
			.hasMessageContaining("não tem trechos");
		assertThatThrownBy(
				() -> LeitorGeoJsonTrechos.ler(entrada("{\"type\":\"FeatureCollection\",\"features\":[{"), JSON))
			.hasMessageContaining("JSON válido");
	}

	@Test
	void csvDeEnderecos() throws Exception {
		var leitura = LeitorCsvEnderecos.ler(entrada("""
				﻿Matricula;Codigo_Logradouro;Numero;Complemento
				2001002;101;15;Casa 2
				2001003;"101";20A;

				2001002;101;30;
				;101;1;
				2001004;;10;
				2001005;101;S/N
				2001006;101;12345678901;
				"""));
		assertThat(leitura.lidos()).isEqualTo(7);
		assertThat(leitura.itens())
			.extracting(l -> l.matricula() + "|" + l.numero() + "|" + l.numeroInteiro() + "|" + l.complemento())
			.containsExactly("2001002|15|15|Casa 2", "2001003|20A|20|null", "2001005|S/N|null|null");
		assertThat(leitura.rejeicoes()).extracting(r -> r.posicao()).containsExactly(5, 6, 7, 9);
		assertThatThrownBy(() -> LeitorCsvEnderecos.ler(entrada("matricula,numero\n1,2\n")))
			.hasMessageContaining("codigo_logradouro");
		assertThatThrownBy(() -> LeitorCsvEnderecos.ler(entrada("matricula;codigo_logradouro;numero\n")))
			.hasMessageContaining("não tem linhas");
		assertThatThrownBy(() -> LeitorCsvEnderecos.ler(entrada(""))).hasMessageContaining("vazio");
	}

	@Test
	void numeroELado() {
		assertThat(Numero.inteiro("123A")).isEqualTo(123);
		assertThat(Numero.inteiro(" 45 ")).isEqualTo(45);
		assertThat(Numero.inteiro("S/N")).isNull();
		assertThat(Numero.inteiro(null)).isNull();
		assertThat(Lado.de(null)).isEqualTo(Lado.AMBOS);
		assertThat(Lado.de("P")).isEqualTo(Lado.PAR);
		assertThat(Lado.de("impar")).isEqualTo(Lado.IMPAR);
		assertThat(Lado.de("A")).isEqualTo(Lado.AMBOS);
	}

	private static ByteArrayInputStream entrada(String texto) {
		return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
	}

}
