package dev.aurora.assistente.controller;

import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.OpenAiSimulado;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADR 0019: o mesmo assistente com a OpenAI, escolhida só por configuração. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssistenteComOpenAiApiTest {

	private static final OpenAiSimulado OPENAI = new OpenAiSimulado();

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@DynamicPropertySource
	static void ia(DynamicPropertyRegistry registro) {
		registro.add("aurora.ia.provedor", () -> "openai");
		registro.add("aurora.ia.openai.chave", () -> "chave-de-teste");
		registro.add("aurora.ia.openai.modelo", () -> "modelo-teste");
		registro.add("aurora.ia.openai.url-base", OPENAI::url);
	}

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Assistente OpenAI");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@openai.teste", t.grupo());
	}

	@AfterAll
	static void parar() {
		OPENAI.close();
	}

	@Test
	void respondeComFontesPelaOpenAi() throws Exception {
		OPENAI.responder(OpenAiSimulado.ferramenta("consultar_adesao", """
				{"unidades":["MUNICIPIO %s"],"incluir_subordinadas":false,"competencia":"2026-08"}"""
			.formatted(t.cidadeA())), OpenAiSimulado.texto("A Cidade A está com 85,41% [1]."));

		mvc.perform(post("/api/v1/assistente/perguntas").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pergunta\": \"Adesão da Cidade A?\"}")
			.with(jwt().jwt(j -> j.issuer(t.emissor()).subject("sub-admin"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagens[1].fontes[0].titulo", is("Adesão à rede de esgoto")))
			.andExpect(jsonPath("$.mensagens[1].numerosSemFonte", hasSize(0)));
		var resultado = OPENAI.pedidos().get(1).get("messages").get(3);
		assertThat(resultado.get("role").asString()).isEqualTo("tool");
		assertThat(resultado.get("content").asString()).contains("85.41");
	}

}
