package dev.aurora.acesso;

import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uma pessoa com perfis diferentes em unidades diferentes: cada permissão vale só onde
 * foi concedida (RN-13, ADR 0013). Antes, as permissões de todas as concessões se somavam
 * em todo o escopo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PermissaoPorUnidadeApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Permissão por unidade");
		DadosDeTeste.criarAdesao(t);
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler", "unidade:ler");
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor local", "indicador:ler", "indicador:gerenciar", "unidade:ler",
				"dados_pessoais:ler", "unidade:gerenciar");
		// Lê a concessão inteira; gere metas, unidades e vê dados pessoais só na Cidade
		// B.
		var misto = DadosDeTeste.criarUsuario(t, "sub-misto", "misto@permissao.teste", t.concessao(), leitura);
		DadosDeTeste.conceder(t, misto, gestor, t.cidadeB());
	}

	@Test
	void leOndeTemLeituraEAlteraSoOndeTemGestao() throws Exception {
		mvc.perform(get("/api/v1/metas").with(token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linhas[*].unidadeId", hasItem(t.cidadeA().toString())));

		mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(meta(t.cidadeA())).with(token()))
			.andExpect(status().isNotFound());
		// Na Cidade B a requisição é autorizada e chega à regra de negócio (já há meta
		// sem fim
		// lá, daí o conflito de vigência); na Cidade A ela nem enxerga a unidade.
		mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(meta(t.cidadeB())).with(token()))
			.andExpect(status().isConflict());
	}

	@Test
	void gestaoDeUnidadesSoNaCidadeB() throws Exception {
		mvc.perform(get("/api/v1/unidades/arvore").with(token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()", is(1)))
			.andExpect(jsonPath("$[0].id", is(t.cidadeB().toString())));
		mvc.perform(post("/api/v1/unidades").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"tipo":"SETOR_COMERCIAL","unidadePaiId":"%s","dados":{"codigo":"A-NOVO","nome":"Novo"}}"""
				.formatted(t.cidadeA()))
			.with(token())).andExpect(status().isUnprocessableContent());
	}

	@Test
	void matriculaCompletaSoNaUnidadeComPermissao() throws Exception {
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeB().toString()).with(token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matriculasVisiveis", is(true)));
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.concessao().toString()).with(token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matriculasVisiveis", is(false)));
	}

	@Test
	void semAPermissaoEmLugarNenhumRecebe403() throws Exception {
		mvc.perform(get("/api/v1/usuarios").with(token())).andExpect(status().isForbidden());
	}

	private static String meta(java.util.UUID unidade) {
		return """
				{"unidadeId":"%s","valor":90,"alerta":80,"vigenciaInicio":"2033-01-01"}""".formatted(unidade);
	}

	private static RequestPostProcessor token() {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject("sub-misto"));
	}

}
