package dev.aurora.acesso.controller;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PerfisApiTest {

	private static Tenant t;

	private static java.util.UUID perfilAdminCustom;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Perfis");
		// O administrador do tenant usa um perfil personalizado (não do sistema), para
		// testar a regra do último admin.
		perfilAdminCustom = DadosDeTeste.criarPerfil(t, "Admin personalizado", "usuario:gerenciar", "unidade:ler");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@perfis.teste", t.grupo(), perfilAdminCustom);
		DadosDeTeste.criarUsuario(t, "sub-admin-cidade", "admin-cidade@perfis.teste", t.cidadeA(), perfilAdminCustom);
	}

	@Test
	void catalogoEListagem() throws Exception {
		mvc.perform(get("/api/v1/permissoes").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.codigo == 'usuario:gerenciar')].area", hasItem("usuario")));
		mvc.perform(get("/api/v1/perfis").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.nome == 'Administrador')].sistema", hasItem(true)));
	}

	@Test
	void ciclodeVidaDePerfilPersonalizado() throws Exception {
		var criado = mvc
			.perform(post("/api/v1/perfis").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nome":"Campo","descricao":"Equipe de campo","permissoes":["mapa:marcar","unidade:ler"]}""")
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		var id = json.readTree(criado.getResponse().getContentAsString()).get("id").asString();

		mvc.perform(post("/api/v1/perfis").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Campo","permissoes":[]}""").with(token("sub-admin"))).andExpect(status().isConflict());
		mvc.perform(put("/api/v1/perfis/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Campo","permissoes":["mapa:marcar","relatorio:exportar"]}""").with(token("sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(put("/api/v1/perfis/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Campo","permissoes":["nao:existe"]}""").with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(delete("/api/v1/perfis/{id}", id).with(token("sub-admin"))).andExpect(status().isNoContent());
	}

	@Test
	void perfilDoSistemaEPerfilEmUsoSaoProtegidos() throws Exception {
		mvc.perform(put("/api/v1/perfis/{id}", t.perfilAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Administrador","permissoes":[]}""").with(token("sub-admin"))).andExpect(status().isConflict());
		mvc.perform(delete("/api/v1/perfis/{id}", t.perfilAdmin()).with(token("sub-admin")))
			.andExpect(status().isConflict());
		mvc.perform(delete("/api/v1/perfis/{id}", perfilAdminCustom).with(token("sub-admin")))
			.andExpect(status().isConflict())
			.andExpect(
					jsonPath("$.detail", is("Perfil em uso por 2 usuários. Retire-o dos usuários antes de remover.")));
	}

	@Test
	void naoDeixaOTenantSemAdministrador() throws Exception {
		mvc.perform(put("/api/v1/perfis/{id}", perfilAdminCustom).contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Admin personalizado","permissoes":["unidade:ler"]}""").with(token("sub-admin")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail",
					is("O tenant ficaria sem administrador: mantenha a permissão de gerenciar usuários.")));
	}

	@Test
	void gestaoDePerfisExigeEscopoNoTenantInteiro() throws Exception {
		mvc.perform(post("/api/v1/perfis").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Local","permissoes":[]}""").with(token("sub-admin-cidade"))).andExpect(status().isForbidden());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
