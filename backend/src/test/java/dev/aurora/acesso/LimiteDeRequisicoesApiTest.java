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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limite de requisições por pessoa (ADR 0037). */
@SpringBootTest(properties = "aurora.seguranca.limite-por-minuto=3")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LimiteDeRequisicoesApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Limite de requisições");
		DadosDeTeste.criarUsuario(t, "sub-ana", "ana@limite.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-bia", "bia@limite.teste", t.grupo());
	}

	@Test
	void acimaDoLimiteRespondeTooManyRequestsSoParaQuemPassou() throws Exception {
		// A janela é o minuto do relógio: não começar perto da virada.
		var segundo = java.time.LocalTime.now(java.time.ZoneOffset.UTC).getSecond();
		if (segundo >= 55) {
			Thread.sleep((61 - segundo) * 1000L);
		}
		for (int i = 0; i < 3; i++) {
			mvc.perform(get("/api/v1/eu").with(token("sub-ana"))).andExpect(status().isOk());
		}
		mvc.perform(get("/api/v1/eu").with(token("sub-ana")))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.status").value(429));

		mvc.perform(get("/api/v1/eu").with(token("sub-bia"))).andExpect(status().isOk());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
