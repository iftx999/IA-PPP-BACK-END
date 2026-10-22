package dev.aurora.auditoria.controller;

import java.util.UUID;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditoriaApiTest {

	private static Tenant t;

	private static UUID admin;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Auditoria");
		admin = DadosDeTeste.criarUsuario(t, "sub-admin", "admin@auditoria.teste", t.grupo());
		var auditor = DadosDeTeste.criarPerfil(t, "Auditor", "auditoria:ler");
		DadosDeTeste.criarUsuario(t, "sub-auditor", "auditor@auditoria.teste", t.grupo(), auditor);
		DadosDeTeste.criarUsuario(t, "sub-auditor-cidade", "auditor-cidade@auditoria.teste", t.cidadeA(), auditor);
	}

	@Test
	void consultaComFiltrosEPaginacao() throws Exception {
		criarSetor(t.cidadeA(), "AUD-A");
		criarSetor(t.cidadeB(), "AUD-B");

		mvc.perform(get("/api/v1/auditoria/eventos").param("tipo", "UNIDADE_CRIADA").with(token("sub-auditor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventos[*].tipo", everyItem(is("UNIDADE_CRIADA"))))
			.andExpect(jsonPath("$.eventos[*].recursoNome", hasItem("Setor AUD-A")))
			.andExpect(jsonPath("$.eventos[*].usuarioEmail", hasItem("admin@auditoria.teste")))
			.andExpect(jsonPath("$.eventos[*].detalhes.codigo", hasItem("AUD-B")));

		// Unidade inclui a subárvore: só o setor criado abaixo da Cidade A.
		mvc.perform(get("/api/v1/auditoria/eventos").param("unidade", t.cidadeA().toString())
			.param("tipo", "UNIDADE_CRIADA")
			.with(token("sub-auditor")))
			.andExpect(jsonPath("$.eventos[*].detalhes.codigo", everyItem(is("AUD-A"))))
			.andExpect(jsonPath("$.eventos", hasSize(1)));

		var primeira = mvc
			.perform(get("/api/v1/auditoria/eventos").param("usuario", admin.toString())
				.param("limite", "1")
				.with(token("sub-auditor")))
			.andExpect(jsonPath("$.eventos", hasSize(1)))
			.andExpect(jsonPath("$.proximo", notNullValue()))
			.andReturn();
		var corpo = json.readTree(primeira.getResponse().getContentAsString());
		mvc.perform(get("/api/v1/auditoria/eventos").param("usuario", admin.toString())
			.param("limite", "1")
			.param("cursor", corpo.get("proximo").asString())
			.with(token("sub-auditor")))
			.andExpect(jsonPath("$.eventos[0].sequencia",
					is((int) (corpo.get("eventos").get(0).get("sequencia").asLong() - 1))));

		mvc.perform(get("/api/v1/auditoria/verificacao").with(token("sub-auditor")))
			.andExpect(jsonPath("$.integra", is(true)));
		mvc.perform(get("/api/v1/auditoria/autores").with(token("sub-auditor")))
			.andExpect(jsonPath("$[*].email", hasItem("admin@auditoria.teste")));
	}

	@Test
	void exportacaoExigePermissaoEFicaNaTrilha() throws Exception {
		criarSetor(t.cidadeB(), "AUD-EXP");
		mvc.perform(get("/api/v1/auditoria/eventos/exportacao.csv").with(token("sub-auditor")))
			.andExpect(status().isForbidden());
		var csv = mvc.perform(get("/api/v1/auditoria/eventos/exportacao.csv").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		// BOM do UTF-8 para o Excel reconhecer a acentuação.
		assertThat(csv).startsWith("\uFEFFsequencia;").contains("UNIDADE_CRIADA");
		mvc.perform(get("/api/v1/auditoria/eventos").param("tipo", "RELATORIO_EXPORTADO").with(token("sub-auditor")))
			.andExpect(jsonPath("$.eventos[0].recurso", is("auditoria/eventos")));
	}

	@Test
	void validaFiltrosEExigeTenantInteiro() throws Exception {
		mvc.perform(get("/api/v1/auditoria/eventos").with(token("sub-auditor-cidade")))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/auditoria/eventos").param("tipo", "NAO_EXISTE").with(token("sub-auditor")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/auditoria/eventos").param("de", "2026-10-10")
			.param("ate", "2026-10-01")
			.with(token("sub-auditor"))).andExpect(status().isUnprocessableContent());
	}

	private void criarSetor(UUID pai, String codigo) throws Exception {
		mvc.perform(post("/api/v1/unidades").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"tipo":"SETOR_COMERCIAL","unidadePaiId":"%s","dados":{"codigo":"%s","nome":"Setor %s"}}"""
				.formatted(pai, codigo, codigo))
			.with(token("sub-admin"))).andExpect(status().isCreated());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
