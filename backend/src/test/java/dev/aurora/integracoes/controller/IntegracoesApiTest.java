package dev.aurora.integracoes.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IntegracoesApiTest {

	private static Tenant t;

	private static Tenant outro;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Integrações");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@integracoes.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-sem", "sem@integracoes.teste", t.grupo(),
				DadosDeTeste.criarPerfil(t, "Sem integrações", "indicador:ler"));
		outro = DadosDeTeste.criarTenant("Outro");
		DadosDeTeste.criarUsuario(outro, "sub-outro", "outro@integracoes.teste", outro.grupo());
	}

	@Test
	void fontesEHistoricoDeCargas() throws Exception {
		var id = criar("CNEFE " + UUID.randomUUID().toString().substring(0, 6), 48);
		mvc.perform(get("/api/v1/integracoes/fontes").with(token(t, "sub-admin")))
			.andExpect(jsonPath("$[?(@.id == '%s')].atrasoMaximoHoras".formatted(id), hasItem(48)))
			.andExpect(jsonPath("$[?(@.id == '%s')].atrasada".formatted(id), hasItem(false)));
		// A fonte do DadosDeTeste tem uma carga em andamento.
		mvc.perform(get("/api/v1/integracoes/fontes/{id}/execucoes", t.fonte()).with(token(t, "sub-admin")))
			.andExpect(jsonPath("$[0].situacao", is("SUCESSO")));

		mvc.perform(put("/api/v1/integracoes/fontes/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"CNEFE ajustado %s","tipo":"ARQUIVO","atrasoMaximoHoras":72,"ativo":false}"""
				.formatted(UUID.randomUUID().toString().substring(0, 4)))
			.with(token(t, "sub-admin"))).andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/integracoes/fontes").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"Comercial","tipo":"ARQUIVO","atrasoMaximoHoras":0,"ativo":true}""")
			.with(token(t, "sub-admin"))).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/integracoes/fontes").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"comercial","tipo":"ARQUIVO","atrasoMaximoHoras":24,"ativo":true}""")
			.with(token(t, "sub-admin"))).andExpect(status().isConflict());
	}

	@Test
	void credencialDoAgenteSoGuardaOHashEPodeSerRevogada() throws Exception {
		var id = criar("Agente " + UUID.randomUUID().toString().substring(0, 6), 24);
		var resposta = json
			.readTree(mvc.perform(post("/api/v1/integracoes/fontes/{id}/credenciais", id).with(token(t, "sub-admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chave", matchesPattern("aur_[a-z0-9]{10}\\.[A-Za-z0-9_-]{43}")))
				.andReturn()
				.getResponse()
				.getContentAsString());
		var prefixo = resposta.get("prefixo").asString();
		var segredo = resposta.get("chave").asString().substring(prefixo.length() + 1);

		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT hash FROM credencial_conector WHERE prefixo = ?")) {
			consulta.setString(1, prefixo);
			try (var r = consulta.executeQuery()) {
				r.next();
				assertThat(r.getBytes(1))
					.isEqualTo(MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8)));
			}
		}
		mvc.perform(get("/api/v1/integracoes/fontes/{id}/credenciais", id).with(token(t, "sub-admin")))
			.andExpect(jsonPath("$[0].prefixo", is(prefixo)))
			.andExpect(jsonPath("$[0].ativa", is(true)));

		// Outro tenant não revoga; o dono revoga uma vez.
		mvc.perform(post("/api/v1/integracoes/credenciais/{p}/revogacao", prefixo).with(token(outro, "sub-outro")))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/integracoes/credenciais/{p}/revogacao", prefixo).with(token(t, "sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/integracoes/credenciais/{p}/revogacao", prefixo).with(token(t, "sub-admin")))
			.andExpect(status().isNotFound());
	}

	@Test
	void exigePermissaoEIsolaTenants() throws Exception {
		mvc.perform(get("/api/v1/integracoes/fontes").with(token(t, "sub-sem"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/integracoes/fontes/{id}/execucoes", t.fonte()).with(token(outro, "sub-outro")))
			.andExpect(status().isNotFound());
	}

	private UUID criar(String nome, int horas) throws Exception {
		var resposta = mvc
			.perform(post("/api/v1/integracoes/fontes").contentType(MediaType.APPLICATION_JSON).content("""
					{"nome":"%s","tipo":"AGENTE","sistemaOrigem":"Sistema X","frequencia":"diária",
					 "atrasoMaximoHoras":%d,"ativo":true}""".formatted(nome, horas)).with(token(t, "sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private static RequestPostProcessor token(Tenant tenant, String sub) {
		return jwt().jwt(j -> j.issuer(tenant.emissor()).subject(sub));
	}

}
