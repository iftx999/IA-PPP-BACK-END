package dev.aurora.contratos.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.DadosDeTeste;
import dev.aurora.PdfDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.contratos.service.ExtratorContrato;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A5 (ADR 0020): aditivo atualiza as metas do contrato sem perder o histórico. Itens
 * cadastrados à mão (IA desligada) para isolar a regra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AditivoApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	ExtratorContrato extrator;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Aditivos");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@aditivos.teste", t.grupo());
	}

	@BeforeEach
	void semIa() {
		when(extrator.disponivel()).thenReturn(false);
	}

	@Test
	void aditivoEncerraNaVesperaSubstituiRetroativoEGuardaHistorico() throws Exception {
		var contrato = criarContrato();
		var original = documento(contrato, "ORIGINAL", "7.2 A adesao minima no Setor A1 sera de 85% ate 2030.");
		var item1 = aprovarMeta(contrato, original, "85", "80", "2026-01-01", "2030-12-31");
		assertThat(metasDoSetor()).containsExactly("2026-01-01|2030-12-31|85.000000");

		// Aditivo a partir de 2027: a meta original termina na véspera.
		var aditivo = documento(contrato, "ADITIVO", "Primeiro termo aditivo. 7.2 A adesao minima passa a 90%.");
		var item2 = aprovarMeta(contrato, aditivo, "90", "85", "2027-01-01", "2030-12-31");
		assertThat(metasDoSetor()).containsExactly("2026-01-01|2026-12-31|85.000000",
				"2027-01-01|2030-12-31|90.000000");
		assertThat(item(contrato, item1).get("status").asString()).isEqualTo("SUBSTITUIDO");

		// Aditivo retroativo (mesmo início): a meta de 90% sai por inteiro e vai para o
		// histórico.
		var segundo = documento(contrato, "ADITIVO", "Segundo termo aditivo. 7.2 A adesao minima passa a 92%.");
		aprovarMeta(contrato, segundo, "92", "88", "2027-01-01", null);
		assertThat(metasDoSetor()).containsExactly("2026-01-01|2026-12-31|85.000000", "2027-01-01|-|92.000000");
		assertThat(item(contrato, item2).get("status").asString()).isEqualTo("SUBSTITUIDO");

		mvc.perform(get("/api/v1/metas/unidades/{id}/historico", t.setorA1()).with(token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].motivo", is("SUBSTITUIDA_POR_ADITIVO")))
			.andExpect(jsonPath("$[0].valorMeta", is(90.0)))
			.andExpect(jsonPath("$[1].motivo", is("ENCERRADA_POR_ADITIVO")))
			.andExpect(jsonPath("$[1].vigenciaFim", is("2030-12-31")))
			.andExpect(jsonPath("$[1].substituidaPorMeta").isNotEmpty());
		assertThat(eventos("META_SUBSTITUIDA")).isEqualTo(1);
	}

	@Test
	void metaManualSobrepostaImpedeOAditivo() throws Exception {
		var contrato = criarContrato();
		var aditivo = documento(contrato, "ADITIVO", "Termo aditivo. 7.3 A adesao minima na Cidade B passa a 80%.");
		var item = criarItem(contrato, aditivo, t.cidadeB(), "80", "75", "2027-01-01", null);
		// A Cidade B tem meta manual sem fim desde 2025 (dados de teste).
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", item).with(token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", containsString("meta manual")));
		assertThat(item(contrato, item).get("status").asString()).isEqualTo("PROPOSTO");
	}

	private UUID aprovarMeta(UUID contrato, UUID documento, String meta, String alerta, String inicio, String fim)
			throws Exception {
		var item = criarItem(contrato, documento, t.setorA1(), meta, alerta, inicio, fim);
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", item).with(token()))
			.andExpect(status().isNoContent());
		return item;
	}

	private UUID criarItem(UUID contrato, UUID documento, UUID unidade, String meta, String alerta, String inicio,
			String fim) throws Exception {
		var resposta = mvc
			.perform(post("/api/v1/contratos/{id}/itens", contrato).contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"documentoId":"%s","item":{"tipo":"META","titulo":"Adesão mínima %s%%","resumo":"Meta","clausula":"7.2",
								 "pagina":1,"trecho":"adesao minima","valorReferencia":%s,"valorAlerta":%s,"unidadeId":"%s",
								 "vigenciaInicio":"%s","dataLimite":%s}}"""
							.formatted(documento, meta, meta, alerta, unidade, inicio,
									fim == null ? "null" : "\"" + fim + "\""))
				.with(token()))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID criarContrato() throws Exception {
		var resposta = mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"numero":"A-%s","unidadeId":"%s","objeto":"Esgotamento sanitário","dataAssinatura":"2025-03-15",
					 "vigenciaFim":"2055-03-14"}""".formatted(UUID.randomUUID().toString().substring(0, 8),
					t.concessao()))
			.with(token())).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID documento(UUID contrato, String tipo, String texto) throws Exception {
		var pdf = PdfDeTeste.comPaginas(List.of(texto + " " + UUID.randomUUID()));
		var resposta = mvc
			.perform(multipart("/api/v1/contratos/{id}/documentos", contrato)
				.file(new MockMultipartFile("arquivo", tipo.toLowerCase() + ".pdf", "application/pdf", pdf))
				.param("tipo", tipo)
				.with(token()))
			.andExpect(status().isCreated())
			.andReturn();
		var id = UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
		for (int tentativa = 0; tentativa < 100; tentativa++) {
			var situacao = json
				.readTree(mvc.perform(get("/api/v1/contratos/documentos/{id}", id).with(token()))
					.andReturn()
					.getResponse()
					.getContentAsString())
				.get("situacao")
				.asString();
			if (situacao.equals("PRONTO")) {
				return id;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("documento não ficou pronto");
	}

	private JsonNode item(UUID contrato, UUID id) throws Exception {
		var itens = json.readTree(mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).with(token()))
			.andReturn()
			.getResponse()
			.getContentAsString());
		for (var item : itens) {
			if (item.get("id").asString().equals(id.toString())) {
				return item;
			}
		}
		throw new AssertionError("item não encontrado");
	}

	private static List<String> metasDoSetor() throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.prepareStatement("""
				SELECT vigencia_inicio || '|' || coalesce(vigencia_fim::text, '-') || '|' || valor_meta
				  FROM meta WHERE tenant_id = ?::uuid AND unidade_id = ?::uuid ORDER BY vigencia_inicio""")) {
			consulta.setString(1, t.id().toString());
			consulta.setString(2, t.setorA1().toString());
			var linhas = consulta.executeQuery();
			var resultado = new java.util.ArrayList<String>();
			while (linhas.next()) {
				resultado.add(linhas.getString(1));
			}
			return resultado;
		}
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

	private static RequestPostProcessor token() {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject("sub-admin"));
	}

}
