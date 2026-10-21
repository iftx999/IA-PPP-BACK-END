package dev.aurora.acesso;

import java.util.UUID;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolamento de ponta a ponta: o tenant e o escopo vêm do token e do banco, nunca da URL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IsolamentoNaApiTest {

	private static Tenant a;

	private static Tenant b;

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void criarTenants() throws Exception {
		a = DadosDeTeste.criarTenant("Tenant A");
		b = DadosDeTeste.criarTenant("Tenant B");
		DadosDeTeste.criarUsuario(a, "sub-admin-a", "admin@a.teste", a.grupo());
		DadosDeTeste.criarUsuario(a, "sub-cidade-a", "cidade@a.teste", a.cidadeA());
		DadosDeTeste.criarUsuario(b, "sub-admin-b", "admin@b.teste", b.grupo());
	}

	@Test
	void semTokenRetorna401() throws Exception {
		mvc.perform(get("/api/v1/unidades")).andExpect(status().isUnauthorized());
	}

	@Test
	void listaSoAsUnidadesDoProprioTenant() throws Exception {
		mvc.perform(get("/api/v1/unidades").with(token(a, "sub-admin-a")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	void unidadeDeOutroTenantRetorna404() throws Exception {
		mvc.perform(get("/api/v1/unidades/{id}", b.cidadeA()).with(token(a, "sub-admin-a")))
			.andExpect(status().isNotFound());
	}

	@Test
	void unidadeForaDoEscopoRetorna404() throws Exception {
		mvc.perform(get("/api/v1/unidades/{id}", a.cidadeB()).with(token(a, "sub-cidade-a")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/unidades").with(token(a, "sub-cidade-a")))
			.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void tokenDeOutroTenantNaoAcessaUsuarioPeloSub() throws Exception {
		// sub válido no tenant B apresentado com o emissor do tenant A
		mvc.perform(get("/api/v1/eu").with(token(a, "sub-admin-b"))).andExpect(status().isForbidden());
	}

	@Test
	void primeiroAcessoVinculaPeloEmailVerificado() throws Exception {
		var sub = "sub-" + UUID.randomUUID();
		DadosDeTeste.criarUsuario(a, null, "convidado@a.teste", a.cidadeA());
		mvc.perform(get("/api/v1/eu").with(jwt().jwt(j -> j.issuer(a.emissor())
			.subject(sub)
			.claim("email", "Convidado@a.teste")
			.claim("email_verified", true))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email", is("convidado@a.teste")));
	}

	private static RequestPostProcessor token(Tenant t, String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
