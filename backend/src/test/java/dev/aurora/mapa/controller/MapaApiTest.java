package dev.aurora.mapa.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cidade A (quadrado de -47,60 a -47,40 × -22,70 a -22,50) com o setor A1 dentro; imóveis
 * do histórico localizados no setor; Cidade B a oeste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MapaApiTest {

	private static Tenant t;

	private static Tenant outro;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Mapa");
		outro = DadosDeTeste.criarTenant("Outro do mapa");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@mapa.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-colega", "colega@mapa.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade-b", "b@mapa.teste", t.cidadeB());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura do mapa", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@mapa.teste", t.grupo(), leitura);
		var exportador = DadosDeTeste.criarPerfil(t, "Exporta sem dados pessoais", "indicador:ler",
				"relatorio:exportar");
		DadosDeTeste.criarUsuario(t, "sub-exporta", "exporta@mapa.teste", t.grupo(), exportador);
		DadosDeTeste.criarUsuario(outro, "sub-outro", "admin@outro-mapa.teste", outro.grupo());

		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute(
					"""
							INSERT INTO municipio_geometria (codigo_ibge, geojson, fonte) VALUES
							  ('3526902', '{"type":"Polygon","coordinates":[[[-47.6,-22.7],[-47.4,-22.7],[-47.4,-22.5],[-47.6,-22.5],[-47.6,-22.7]]]}', 'teste'),
							  ('3538709', '{"type":"Polygon","coordinates":[[[-47.8,-22.8],[-47.62,-22.8],[-47.62,-22.6],[-47.8,-22.6],[-47.8,-22.8]]]}', 'teste')
							ON CONFLICT (codigo_ibge) DO NOTHING""");
			s.execute("""
					INSERT INTO setor_geometria (tenant_id, unidade_id, geom, origem) VALUES ('%s', '%s',
					  ST_Multi(ST_MakeEnvelope(-47.52, -22.62, -47.48, -22.58, 4674)), 'GERADO')""".formatted(t.id(),
					t.setorA1()));
			localizar(s, t, "2001001", t.setorA1(), -47.5, -22.6, "ENDERECO");
			localizar(s, t, "2001002", t.setorA1(), -47.5001, -22.6, "ENDERECO");
			localizar(s, t, "2001006", t.setorA1(), -47.495, -22.595, "BAIRRO");
			localizar(s, t, "B-001", t.cidadeB(), -47.7, -22.7, "ENDERECO");
			localizar(s, outro, "A-001", outro.setorA1(), -47.5, -22.6, "ENDERECO");
			s.execute(
					"""
							INSERT INTO unidade (id, tenant_id, tipo, unidade_pai_id, codigo, nome, caminho, latitude, longitude)
							VALUES ('%1$s', '%2$s', 'ETE', '%3$s', 'ETE-A', 'ETE Cidade A', '%4$s', -22.55, -47.45)"""
						.formatted(UUID.randomUUID(), t.id(), t.cidadeA(),
								DadosDeTeste.rotulo(t.grupo(), t.concessao(), t.cidadeA()) + ".ete"));
		}
	}

	@Test
	void camadasComAdesaoDosMunicipiosSetoresEEstacoes() throws Exception {
		mvc.perform(get("/api/v1/mapa/camadas").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.competencia", is("2026-08")))
			.andExpect(jsonPath("$.competencias[0]", is("2026-08")))
			.andExpect(jsonPath("$.municipios[*].codigoIbge", containsInAnyOrder("3526902", "3538709")))
			// Cidade A 85,41% com meta 85: atingida; Cidade B 67,98% abaixo do alerta 70.
			.andExpect(jsonPath("$.municipios[?(@.codigoIbge == '3526902')].situacao").value(contains("META_ATINGIDA")))
			.andExpect(
					jsonPath("$.municipios[?(@.codigoIbge == '3538709')].situacao").value(contains("ABAIXO_DO_ALERTA")))
			.andExpect(jsonPath("$.municipios[0].geometria.type", is("Polygon")))
			.andExpect(jsonPath("$.setores", hasSize(1)))
			.andExpect(jsonPath("$.setores[0].geometria.type", is("MultiPolygon")))
			.andExpect(jsonPath("$.operacionais[0].nome", is("ETE Cidade A")))
			.andExpect(jsonPath("$.enquadramento.oeste", is(-47.8)))
			.andExpect(jsonPath("$.zoomImoveis", is(15)));
	}

	@Test
	void camadasRespeitamOEscopo() throws Exception {
		mvc.perform(get("/api/v1/mapa/camadas").with(token("sub-cidade-b")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.municipios[*].codigoIbge", contains("3538709")))
			.andExpect(jsonPath("$.setores", hasSize(0)))
			.andExpect(jsonPath("$.operacionais", hasSize(0)));
	}

	@Test
	void tilesTrazemImoveisAgrupamentosERedeConformeOZoom() throws Exception {
		var perto = tile(15, -47.5, -22.6, "sub-admin", "").andExpect(status().isOk())
			.andExpect(content().contentType("application/vnd.mapbox-vector-tile"))
			.andExpect(header().string("Cache-Control", "max-age=300, private"))
			.andReturn();
		assertThat(texto(perto.getResponse().getContentAsByteArray())).contains("imoveis", "CORTADO", "situacao");

		var longe = tile(10, -47.5, -22.6, "sub-admin", "").andExpect(status().isOk()).andReturn();
		assertThat(texto(longe.getResponse().getContentAsByteArray())).contains("agrupamentos", "quantidade")
			.doesNotContain("CORTADO");

		// Só ligados com localização exata: o único ligado do setor é aproximado.
		tile(15, -47.5, -22.6, "sub-admin", "situacoes=LIGADO&aproximadas=false").andExpect(status().isNoContent());
		tile(15, -40.0, -10.0, "sub-admin", "").andExpect(status().isNoContent());
	}

	@Test
	void tileDeOutroTenantOuForaDoEscopoVemVazio() throws Exception {
		// O outro tenant tem um imóvel no mesmo ponto, mas da competência ago/2026 só o
		// dele.
		var doOutro = tile(15, -47.5, -22.6, "sub-outro", "").andExpect(status().isOk()).andReturn();
		assertThat(texto(doOutro.getResponse().getContentAsByteArray())).contains("LIGADO").doesNotContain("CORTADO");
		tile(15, -47.5, -22.6, "sub-cidade-b", "").andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/mapa/tiles/3/9/0.mvt").with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void imoveisNoPontoComMatriculaConformeAPermissao() throws Exception {
		long antes = eventos("ACESSO_DADO_PESSOAL");
		mvc.perform(get("/api/v1/mapa/imoveis").param("longitude", "-47.5")
			.param("latitude", "-22.6")
			.param("raio", "30")
			.with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].matricula", contains("2001001", "2001002")))
			.andExpect(jsonPath("$[0].situacao", is("CORTADO")))
			.andExpect(jsonPath("$[0].aproximada", is(false)));
		assertThat(eventos("ACESSO_DADO_PESSOAL")).isEqualTo(antes + 1);

		mvc.perform(get("/api/v1/mapa/imoveis").param("longitude", "-47.5")
			.param("latitude", "-22.6")
			.param("raio", "30")
			.with(token("sub-leitura"))).andExpect(jsonPath("$[*].matricula", contains("••••001", "••••002")));
		assertThat(eventos("ACESSO_DADO_PESSOAL")).isEqualTo(antes + 1);

		mvc.perform(get("/api/v1/mapa/imoveis").param("longitude", "-47.5")
			.param("latitude", "-22.6")
			.param("raio", "30")
			.with(token("sub-outro"))).andExpect(jsonPath("$[*].matricula", contains("A-001")));
		mvc.perform(get("/api/v1/mapa/imoveis").param("longitude", "10")
			.param("latitude", "-22.6")
			.with(token("sub-admin"))).andExpect(status().isBadRequest());
	}

	@Test
	void marcacaoNoSetorSoQuemCriouRemove() throws Exception {
		long criadas = eventos("MARCACAO_CRIADA");
		var criada = mvc.perform(post("/api/v1/mapa/marcacoes").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"longitude": -47.501, "latitude": -22.601, "titulo": "  Visita à rua 3 ", "observacao": " "}""")
			.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", notNullValue()))
			.andExpect(jsonPath("$.unidadeId", is(t.setorA1().toString())))
			.andExpect(jsonPath("$.titulo", is("Visita à rua 3")))
			.andExpect(jsonPath("$.observacao").doesNotExist())
			.andExpect(jsonPath("$.autorNome", is("admin@mapa.teste")))
			.andExpect(jsonPath("$.podeRemover", is(true)))
			.andReturn();
		var id = json.readTree(criada.getResponse().getContentAsString()).get("id").asString();
		assertThat(eventos("MARCACAO_CRIADA")).isEqualTo(criadas + 1);

		mvc.perform(get("/api/v1/mapa/marcacoes").param("oeste", "-47.6")
			.param("sul", "-22.7")
			.param("leste", "-47.4")
			.param("norte", "-22.5")
			.with(token("sub-colega")))
			.andExpect(jsonPath("$[?(@.id == '%s')].podeRemover", id).value(contains(false)));
		mvc.perform(get("/api/v1/mapa/marcacoes").param("oeste", "-47.6")
			.param("sul", "-22.7")
			.param("leste", "-47.4")
			.param("norte", "-22.5")
			.with(token("sub-outro"))).andExpect(jsonPath("$", hasSize(0)));

		mvc.perform(delete("/api/v1/mapa/marcacoes/{id}", id).with(token("sub-colega")))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/mapa/marcacoes/{id}", id).with(token("sub-outro")))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/mapa/marcacoes/{id}", id).with(token("sub-admin")))
			.andExpect(status().isNoContent());
		assertThat(eventos("MARCACAO_REMOVIDA")).isEqualTo(1);
	}

	@Test
	void marcacaoNaCidadeForaDasUnidadesESemPermissao() throws Exception {
		marcar(-47.45, -22.65, "sub-admin").andExpect(status().isCreated())
			.andExpect(jsonPath("$.unidadeId", is(t.cidadeA().toString())));
		marcar(-46.0, -22.0, "sub-admin").andExpect(status().isUnprocessableContent());
		// Quem só atua na Cidade B não marca na Cidade A.
		marcar(-47.45, -22.65, "sub-cidade-b").andExpect(status().isUnprocessableContent());
		marcar(-47.45, -22.65, "sub-leitura").andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/mapa/marcacoes").param("oeste", "-47.4")
			.param("sul", "-22.7")
			.param("leste", "-47.6")
			.param("norte", "-22.5")
			.with(token("sub-admin"))).andExpect(status().isBadRequest());
	}

	@Test
	void exportaCsvDaAreaVisivelComAuditoria() throws Exception {
		long antes = eventos("RELATORIO_EXPORTADO");
		var resposta = exportar(
				"""
						{"formato": "CSV", "area": {"oeste": -47.6, "sul": -22.7, "leste": -47.4, "norte": -22.5},
						 "imoveis": {"competencia": "2026-08", "situacoes": ["FACTIVEL", "CORTADO", "LIGADO"], "aproximadas": true},
						 "marcacoes": true}""",
				"sub-admin")
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"area-do-mapa-2026-08.csv\""))
			.andReturn();
		var csv = new String(resposta.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿tipo;matricula;situacao;localizacao;unidade;latitude;longitude")
			.contains("Imóvel;2001001;Cortado;Exata;")
			.contains("Imóvel;2001006;Ligado;Aproximada;")
			.contains(";-22.600000;-47.500000;")
			.doesNotContain("B-001");
		assertThat(eventos("RELATORIO_EXPORTADO")).isEqualTo(antes + 1);

		var semDadosPessoais = exportar("""
				{"formato": "CSV", "area": {"oeste": -47.6, "sul": -22.7, "leste": -47.4, "norte": -22.5},
				 "imoveis": {"situacoes": ["CORTADO"], "aproximadas": false}, "marcacoes": false}""", "sub-exporta")
			.andExpect(status().isOk())
			.andReturn();
		assertThat(new String(semDadosPessoais.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).lines())
			.containsExactly(
					"﻿tipo;matricula;situacao;localizacao;unidade;latitude;longitude;titulo;observacao;autor;data",
					"Imóvel;••••001;Cortado;Exata;SETOR_COMERCIAL " + t.setorA1() + ";-22.600000;-47.500000;;;;");
	}

	@Test
	void exportaPdfComImagemDoMapa() throws Exception {
		var pedido = """
				{"formato": "PDF", "area": {"oeste": -47.6, "sul": -22.7, "leste": -47.4, "norte": -22.5},
				 "imoveis": {"aproximadas": true}, "marcacoes": true, "imagemPng": "%s"}""";
		var pdf = exportar(pedido.formatted(png(40, 20)), "sub-admin").andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andReturn();
		assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
			.isEqualTo("%PDF-");

		exportar(pedido.formatted(Base64.getEncoder().encodeToString("não é png".getBytes(StandardCharsets.UTF_8))),
				"sub-admin")
			.andExpect(status().isUnprocessableContent());
		exportar(pedido.formatted(png(5000, 1)), "sub-admin").andExpect(status().isUnprocessableContent());
		exportar(pedido.formatted(""), "sub-leitura").andExpect(status().isForbidden());
		exportar("""
				{"formato": "CSV", "area": {"oeste": -47.6, "sul": -22.7, "leste": -47.4, "norte": -22.5},
				 "marcacoes": false}""", "sub-admin").andExpect(status().isUnprocessableContent());
	}

	private ResultActions tile(int z, double longitude, double latitude, String sub, String consulta) throws Exception {
		int n = 1 << z;
		int x = (int) Math.floor((longitude + 180) / 360 * n);
		double rad = Math.toRadians(latitude);
		int y = (int) Math.floor((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2 * n);
		return mvc.perform(
				get("/api/v1/mapa/tiles/{z}/{x}/{y}.mvt?competencia=2026-08&" + consulta, z, x, y).with(token(sub)));
	}

	private ResultActions marcar(double longitude, double latitude, String sub) throws Exception {
		return mvc.perform(post("/api/v1/mapa/marcacoes").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"longitude": %s, "latitude": %s, "titulo": "Visita"}""".formatted(longitude, latitude))
			.with(token(sub)));
	}

	private ResultActions exportar(String corpo, String sub) throws Exception {
		return mvc.perform(post("/api/v1/mapa/exportacao").contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.with(token(sub)));
	}

	private static String png(int largura, int altura) throws Exception {
		var imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
		var saida = new ByteArrayOutputStream();
		ImageIO.write(imagem, "png", saida);
		return Base64.getEncoder().encodeToString(saida.toByteArray());
	}

	private static String texto(byte[] tile) {
		return new String(tile, StandardCharsets.ISO_8859_1);
	}

	private static void localizar(java.sql.Statement s, Tenant tenant, String matricula, UUID unidade, double longitude,
			double latitude, String precisao) throws Exception {
		s.execute("""
				INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
				VALUES ('%s', '%s', '%s', ST_SetSRID(ST_MakePoint(%s, %s), 4674), 'GEOCODIFICADA', '%s')"""
			.formatted(tenant.id(), matricula, unidade, longitude, latitude, precisao));
	}

	private static long eventos(String tipo) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c
					.prepareStatement("SELECT count(*) FROM evento_auditoria WHERE tenant_id = ?::uuid AND tipo = ?")) {
			consulta.setString(1, t.id().toString());
			consulta.setString(2, tipo);
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getLong(1);
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(sub.equals("sub-outro") ? outro.emissor() : t.emissor()).subject(sub));
	}

}
