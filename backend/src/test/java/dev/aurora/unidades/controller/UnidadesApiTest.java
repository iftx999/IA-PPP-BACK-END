package dev.aurora.unidades.controller;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UnidadesApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Unidades");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@unidades.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade-a", "cidade-a@unidades.teste", t.cidadeA());
	}

	@Test
	void criaEditaEValidaAHierarquia() throws Exception {
		var id = criar("SETOR_COMERCIAL", t.cidadeB(), """
				{"codigo":" b-sul ","nome":"Cidade B Sul","latitude":-23.5,"longitude":-46.6}""");
		mvc.perform(get("/api/v1/unidades/arvore").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].codigo".formatted(id), hasItem("B-SUL")))
			.andExpect(jsonPath("$[?(@.id == '%s')].nivel".formatted(id), hasItem(4)));

		mvc.perform(put("/api/v1/unidades/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
				{"codigo":"B-SUL","nome":"Setor Sul"}""").with(token("sub-admin"))).andExpect(status().isNoContent());

		// Código repetido, tipo no lugar errado e município sem IBGE.
		novo("SETOR_COMERCIAL", t.cidadeB(), """
				{"codigo":"B-SUL","nome":"Outro"}""").andExpect(status().isConflict());
		novo("SETOR_COMERCIAL", t.concessao(), """
				{"codigo":"X1","nome":"Setor solto"}""").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", is("Setor comercial não pode ficar abaixo de Concessão / SPE.")));
		novo("MUNICIPIO", t.concessao(), """
				{"codigo":"X2","nome":"Sem IBGE"}""").andExpect(status().isUnprocessableContent());
		novo("GRUPO", t.grupo(), """
				{"codigo":"X3","nome":"Outro grupo"}""").andExpect(status().isUnprocessableContent());
	}

	@Test
	void moverReescreveOCaminhoDaSubarvore() throws Exception {
		var concessaoB = criar("CONCESSAO", t.grupo(), """
				{"codigo":"CONC-B","nome":"Concessão B"}""");
		var cidade = criar("MUNICIPIO", t.concessao(), """
				{"codigo":"CIDADE-M","nome":"Cidade Móvel","codigoIbge":"3550308"}""");
		var setor = criar("SETOR_COMERCIAL", cidade, """
				{"codigo":"M-1","nome":"Setor M1"}""");

		mover(cidade, concessaoB).andExpect(status().isNoContent());
		assertThat(caminho(setor)).isEqualTo(DadosDeTeste.rotulo(t.grupo(), concessaoB, cidade, setor));

		mover(t.concessao(), cidade).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", is("Concessão / SPE não pode ficar abaixo de Município.")));
		mover(cidade, setor).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", is("Não é possível mover uma unidade para dentro dela mesma.")));
		mover(t.grupo(), concessaoB).andExpect(status().isUnprocessableContent());
	}

	@Test
	void inativaSoSemFilhasAtivasNemAcessos() throws Exception {
		var cidade = criar("MUNICIPIO", t.concessao(), """
				{"codigo":"CIDADE-I","nome":"Cidade Inativável","codigoIbge":"3509502"}""");
		var ete = criar("ETE", cidade, """
				{"codigo":"ETE-I","nome":"ETE I"}""");

		situacao(cidade, false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", is("Há 1 unidade ativa abaixo desta. Inative-as ou mova-as antes.")));
		situacao(ete, false).andExpect(status().isNoContent());
		situacao(cidade, false).andExpect(status().isNoContent());
		situacao(ete, true).andExpect(status().isConflict());
		situacao(cidade, true).andExpect(status().isNoContent());

		// Cidade A é escopo do usuário sub-cidade-a.
		situacao(t.setorA1(), false).andExpect(status().isNoContent());
		situacao(t.cidadeA(), false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail",
					is("A unidade é escopo de 1 concessão de acesso. Retire-as em Usuários " + "antes de inativar.")));
		situacao(t.setorA1(), true).andExpect(status().isNoContent());
	}

	@Test
	void respeitaOEscopoEOTenant() throws Exception {
		// Escopo na Cidade A: não vê a Cidade B nem cria abaixo dela.
		mvc.perform(post("/api/v1/unidades").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"tipo":"SETOR_COMERCIAL","unidadePaiId":"%s","dados":{"codigo":"FORA","nome":"Fora"}}"""
				.formatted(t.cidadeB()))
			.with(token("sub-cidade-a"))).andExpect(status().isUnprocessableContent());
		mvc.perform(put("/api/v1/unidades/{id}", t.cidadeB()).contentType(MediaType.APPLICATION_JSON).content("""
				{"codigo":"X","nome":"X","codigoIbge":"3538709"}""").with(token("sub-cidade-a")))
			.andExpect(status().isNotFound());
		var outro = DadosDeTeste.criarTenant("Outro");
		mover(outro.cidadeA(), t.concessao()).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/unidades/tipos").with(token("sub-cidade-a")))
			.andExpect(jsonPath("$[?(@.codigo == 'ETE')].tiposPai[0]", hasItem("MUNICIPIO")));
	}

	private UUID criar(String tipo, UUID pai, String dados) throws Exception {
		var resposta = novo(tipo, pai, dados).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private org.springframework.test.web.servlet.ResultActions novo(String tipo, UUID pai, String dados)
			throws Exception {
		return mvc.perform(post("/api/v1/unidades").contentType(MediaType.APPLICATION_JSON).content("""
				{"tipo":"%s","unidadePaiId":"%s","dados":%s}""".formatted(tipo, pai, dados)).with(token("sub-admin")));
	}

	private org.springframework.test.web.servlet.ResultActions mover(UUID id, UUID pai) throws Exception {
		return mvc.perform(put("/api/v1/unidades/{id}/pai", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"unidadePaiId\":\"%s\"}".formatted(pai))
			.with(token("sub-admin")));
	}

	private org.springframework.test.web.servlet.ResultActions situacao(UUID id, boolean ativo) throws Exception {
		return mvc.perform(put("/api/v1/unidades/{id}/situacao", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"ativo\":%s}".formatted(ativo))
			.with(token("sub-admin")));
	}

	private static String caminho(UUID unidade) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT caminho::text FROM unidade WHERE id = ?::uuid")) {
			consulta.setString(1, unidade.toString());
			try (var r = consulta.executeQuery()) {
				r.next();
				return r.getString(1);
			}
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
