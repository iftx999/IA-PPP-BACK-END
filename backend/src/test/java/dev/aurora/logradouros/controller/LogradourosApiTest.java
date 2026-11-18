package dev.aurora.logradouros.controller;

import java.nio.charset.StandardCharsets;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0043 (J3): simulação e importação dos trechos por município, reimportação sem
 * duplicar, endereços dos imóveis com o trecho escolhido pela faixa, matrícula só para
 * quem vê dados pessoais, correção da faixa, cobertura e permissão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogradourosApiTest {

	private static final String TRECHOS = """
			{"type":"FeatureCollection","features":[
			 {"type":"Feature","properties":{"codigo_logradouro":"101","tipo":"Rua","nome":"Rua São João","bairro":"Centro",
			   "codigo_trecho":"T1","numero_inicial":1,"numero_final":99,"lado":"IMPAR"},
			  "geometry":{"type":"LineString","coordinates":[[-47.0600,-22.9000],[-47.0550,-22.9000]]}},
			 {"type":"Feature","properties":{"codigo_logradouro":"101","tipo":"Rua","nome":"Rua São João","bairro":"Centro",
			   "codigo_trecho":"T2","numero_inicial":2,"numero_final":100,"lado":"PAR"},
			  "geometry":{"type":"LineString","coordinates":[[-47.0600,-22.9001],[-47.0550,-22.9001]]}},
			 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Avenida Brasil","codigo_trecho":"T3"},
			  "geometry":{"type":"MultiLineString","coordinates":[[[-47.07,-22.91],[-47.06,-22.91]],[[-47.03,-22.95],[-47.02,-22.95]]]}},
			 {"type":"Feature","properties":{"codigo_logradouro":"102","nome":"Avenida Brasil","codigo_trecho":"T4"},
			  "geometry":{"type":"LineString","coordinates":[[-22.91,-47.07],[-22.91,-47.06]]}}
			]}""";

	private static final String ENDERECOS = """
			matricula;codigo_logradouro;numero;complemento
			2001002;101;15;Casa 2
			2001003;101;20;
			2001004;101;S/N;
			B-001;101;10;
			9999999;101;5;
			2001005;999;7;
			""";

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Logradouros");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "logradouro:gerenciar");
		DadosDeTeste.criarUsuario(t, "sub-gestor-logradouros", "gestor@logradouros.teste", t.grupo(), gestor);
		var comDados = DadosDeTeste.criarPerfil(t, "Gestor com dados", "logradouro:gerenciar", "dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-dados-logradouros", "dados@logradouros.teste", t.grupo(), comDados);
		var leitor = DadosDeTeste.criarPerfil(t, "Leitor", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitor-logradouros", "leitor@logradouros.teste", t.grupo(), leitor);
	}

	@Test
	void doArquivoAoTrechoDeCadaImovel() throws Exception {
		// Simulação: relatório completo, nada gravado.
		trechos(true).andExpect(status().isOk())
			.andExpect(jsonPath("$.simulacao").value(true))
			.andExpect(jsonPath("$.lidos").value(4))
			.andExpect(jsonPath("$.novos").value(2))
			.andExpect(jsonPath("$.rejeitados").value(2))
			.andExpect(jsonPath("$.rejeicoes[0].posicao").value(3))
			.andExpect(jsonPath("$.rejeicoes[0].motivo").value(org.hamcrest.Matchers.containsString("linha válida")))
			.andExpect(jsonPath("$.rejeicoes[1].posicao").value(4));
		assertThat(contar("SELECT count(*) FROM trecho WHERE tenant_id = '%s'")).isZero();

		trechos(false).andExpect(status().isOk()).andExpect(jsonPath("$.novos").value(2));
		// Reimportar o mesmo arquivo não duplica nem altera.
		trechos(false).andExpect(status().isOk())
			.andExpect(jsonPath("$.novos").value(0))
			.andExpect(jsonPath("$.atualizados").value(0))
			.andExpect(jsonPath("$.inalterados").value(2));
		assertThat(contar("SELECT count(*) FROM logradouro WHERE tenant_id = '%s'")).isEqualTo(1);
		// Só por município.
		mvc.perform(multipart("/api/v1/logradouros/importacoes/trechos").file(arquivo("ruas.geojson", TRECHOS))
			.param("unidade", t.setorA1().toString())
			.with(token("sub-gestor-logradouros"))).andExpect(status().isUnprocessableContent());

		mvc.perform(multipart("/api/v1/logradouros/importacoes/enderecos").file(arquivo("enderecos.csv", ENDERECOS))
			.with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.novos").value(3))
			.andExpect(jsonPath("$.rejeitados").value(3))
			// S/N sem ponto no mapa e com dois trechos: fica sem trecho.
			.andExpect(jsonPath("$.semTrecho").value(1))
			.andExpect(jsonPath("$.rejeicoes[0].motivo").value("Logradouro 101 não existe no município do imóvel."))
			.andExpect(jsonPath("$.rejeicoes[1].motivo")
				.value("Matrícula 9999999 não está na carga mais recente do seu escopo."))
			.andExpect(jsonPath("$.rejeicoes[2].motivo").value("Logradouro 999 não existe no município do imóvel."));

		var lista = mvc
			.perform(get("/api/v1/logradouros").param("unidade", t.cidadeA().toString())
				.param("busca", "sao joao")
				.with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.itens[0].nome").value("Rua São João"))
			.andExpect(jsonPath("$.itens[0].trechos").value(2))
			.andExpect(jsonPath("$.itens[0].imoveis").value(3))
			.andReturn()
			.getResponse()
			.getContentAsString();
		var id = json.readTree(lista).get("itens").get(0).get("id").asString();

		mvc.perform(get("/api/v1/logradouros/{id}", id).with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.trechos", hasSize(2)))
			.andExpect(jsonPath("$.trechos[0].codigo").value("T1"))
			.andExpect(jsonPath("$.trechos[0].imoveis").value(1))
			.andExpect(jsonPath("$.trechos[0].coordenadas[0][0]").value(-47.06))
			.andExpect(jsonPath("$.trechos[1].codigo").value("T2"))
			.andExpect(jsonPath("$.trechos[1].imoveis").value(1));

		// Sem dados_pessoais:ler a matrícula vem mascarada; com, inteira e na trilha.
		mvc.perform(get("/api/v1/logradouros/{id}/imoveis", id).with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].matricula").value("••••002"))
			.andExpect(jsonPath("$[0].numero").value("15"))
			.andExpect(jsonPath("$[0].trecho").value("T1"))
			.andExpect(jsonPath("$[1].trecho").value("T2"))
			.andExpect(jsonPath("$[2].numero").value("S/N"));
		mvc.perform(get("/api/v1/logradouros/{id}/imoveis", id).with(token("sub-dados-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].matricula").value("2001002"));
		assertThat(contar("""
				SELECT count(*) FROM evento_auditoria WHERE tenant_id = '%s' AND tipo = 'ACESSO_DADO_PESSOAL'
				   AND recurso = 'logradouro/""" + id + "'")).isEqualTo(1);

		// Corrigir a faixa: T1 passa a cobrir os dois lados e fica com os números de 1 a
		// 99.
		var t1 = json
			.readTree(mvc.perform(get("/api/v1/logradouros/{id}", id).with(token("sub-gestor-logradouros")))
				.andReturn()
				.getResponse()
				.getContentAsString())
			.get("trechos")
			.get(0)
			.get("id")
			.asString();
		mvc.perform(put("/api/v1/logradouros/trechos/{id}", t1).contentType(MediaType.APPLICATION_JSON)
			.content("{\"numeroInicial\":1,\"numeroFinal\":99,\"lado\":\"AMBOS\"}")
			.with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.trechos[0].lado").value("AMBOS"))
			.andExpect(jsonPath("$.trechos[0].imoveis").value(2));
		mvc.perform(put("/api/v1/logradouros/trechos/{id}", t1).contentType(MediaType.APPLICATION_JSON)
			.content("{\"numeroInicial\":50,\"numeroFinal\":10,\"lado\":\"AMBOS\"}")
			.with(token("sub-gestor-logradouros"))).andExpect(status().isUnprocessableContent());
		mvc.perform(put("/api/v1/logradouros/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"tipo\":\"Rua\",\"nome\":\"Rua São João Batista\",\"bairro\":\"Centro\"}")
			.with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logradouro.nome").value("Rua São João Batista"));

		mvc.perform(get("/api/v1/logradouros/cobertura").param("unidade", t.cidadeA().toString())
			.with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imoveis").value(7))
			.andExpect(jsonPath("$.comEndereco").value(3))
			.andExpect(jsonPath("$.comTrecho").value(2))
			.andExpect(jsonPath("$.aproximados").value(7));

		// Histórico: só as importações de verdade.
		mvc.perform(get("/api/v1/logradouros/importacoes").with(token("sub-gestor-logradouros")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].tipo").value("ENDERECOS"));
		mvc.perform(get("/api/v1/logradouros").with(token("sub-leitor-logradouros"))).andExpect(status().isForbidden());
	}

	private ResultActions trechos(boolean simular) throws Exception {
		return mvc.perform(multipart("/api/v1/logradouros/importacoes/trechos").file(arquivo("ruas.geojson", TRECHOS))
			.param("unidade", t.cidadeA().toString())
			.param("simular", String.valueOf(simular))
			.with(token("sub-gestor-logradouros")));
	}

	private static MockMultipartFile arquivo(String nome, String conteudo) {
		return new MockMultipartFile("arquivo", nome, "application/octet-stream",
				conteudo.getBytes(StandardCharsets.UTF_8));
	}

	private static long contar(String sql) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var s = c.createStatement();
				var r = s.executeQuery(sql.formatted(t.id()))) {
			r.next();
			return r.getLong(1);
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
