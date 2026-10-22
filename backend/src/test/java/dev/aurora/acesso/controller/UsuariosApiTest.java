package dev.aurora.acesso.controller;

import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.service.ProvedorIdentidade;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UsuariosApiTest {

	private static Tenant t;

	private static UUID admin;

	private static UUID perfilLeitura;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	ProvedorIdentidade identidade;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Usuários");
		admin = DadosDeTeste.criarUsuario(t, "sub-admin", "admin@usuarios.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-admin-cidade", "admin-cidade@usuarios.teste", t.cidadeA());
		perfilLeitura = DadosDeTeste.criarPerfil(t, "Leitura", "unidade:ler");
	}

	@BeforeEach
	void provedor() {
		reset(identidade);
		when(identidade.criarConta(eq(t.emissor()), anyString(), anyString()))
			.thenAnswer(chamada -> "sub-" + chamada.getArgument(1));
	}

	@Test
	void convidaListaEAlteraConcessoes() throws Exception {
		var id = convidar("nova.pessoa@usuarios.teste", perfilLeitura, t.cidadeB());
		verify(identidade).enviarConvite(t.emissor(), "sub-nova.pessoa@usuarios.teste");

		mvc.perform(get("/api/v1/usuarios/{id}", id).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao", is("CONVITE_PENDENTE")))
			.andExpect(jsonPath("$.concessoes[0].perfil", is("Leitura")))
			.andExpect(jsonPath("$.concessoes[0].unidadeId", is(t.cidadeB().toString())));
		mvc.perform(get("/api/v1/usuarios").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].email", hasItem("nova.pessoa@usuarios.teste")));

		mvc.perform(put("/api/v1/usuarios/{id}/concessoes", id).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"concessoes":[{"perfilId":"%s","unidadeId":"%s"},{"perfilId":"%s","unidadeId":"%s"},
					               {"perfilId":"%s","unidadeId":"%s"}]}""".formatted(perfilLeitura, t.cidadeA(),
					perfilLeitura, t.setorA1(), perfilLeitura, t.cidadeA()))
			.with(token("sub-admin"))).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/usuarios/{id}", id).with(token("sub-admin")))
			.andExpect(jsonPath("$.concessoes", hasSize(2)));

		mvc.perform(post("/api/v1/usuarios/{id}/convite", id).with(token("sub-admin")))
			.andExpect(status().isAccepted());
		mvc.perform(post("/api/v1/usuarios/{id}/redefinicao-senha", id).with(token("sub-admin")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", is("O convite ainda não foi aceito: reenvie o convite.")));
		assertThat(eventos(id)).contains("USUARIO_CONVIDADO", "ACESSO_ALTERADO", "CONVITE_REENVIADO");
	}

	@Test
	void conviteValidaEmailRepetidoUnidadeEPerfil() throws Exception {
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
			.content(
					"""
							{"nome":"Repetido","email":"ADMIN@usuarios.teste","concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}"""
						.formatted(perfilLeitura, t.cidadeA()))
			.with(token("sub-admin"))).andExpect(status().isConflict());
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"Fora","email":"fora@usuarios.teste","concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}"""
				.formatted(perfilLeitura, UUID.randomUUID()))
			.with(token("sub-admin"))).andExpect(status().isUnprocessableContent());
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Sem","email":"sem@usuarios.teste","concessoes":[]}""").with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome":"Inválido","email":"nao-e-email","concessoes":[]}""").with(token("sub-admin")))
			.andExpect(status().isBadRequest());
		verify(identidade, never()).enviarConvite(anyString(), anyString());
	}

	@Test
	void falhaNoProvedorNaoGravaOConvite() throws Exception {
		doThrow(new ServicoExternoIndisponivelException("O provedor de identidade não respondeu.", null))
			.when(identidade)
			.enviarConvite(anyString(), anyString());
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"Falha","email":"falha@usuarios.teste","concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}"""
				.formatted(perfilLeitura, t.cidadeA()))
			.with(token("sub-admin"))).andExpect(status().isServiceUnavailable());
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT count(*) FROM usuario WHERE email = 'falha@usuarios.teste'");
				var r = consulta.executeQuery()) {
			r.next();
			assertThat(r.getLong(1)).isZero();
		}
	}

	@Test
	void desativaEReativaPreservandoOUltimoAdministrador() throws Exception {
		var id = convidar("temporario@usuarios.teste", perfilLeitura, t.cidadeA());
		mvc.perform(put("/api/v1/usuarios/{id}/situacao", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"ativo\":false}")
			.with(token("sub-admin"))).andExpect(status().isNoContent());
		verify(identidade).definirAtiva(t.emissor(), "sub-temporario@usuarios.teste", false);
		mvc.perform(get("/api/v1/usuarios/{id}", id).with(token("sub-admin")))
			.andExpect(jsonPath("$.situacao", is("INATIVO")));
		mvc.perform(put("/api/v1/usuarios/{id}/situacao", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"ativo\":true}")
			.with(token("sub-admin"))).andExpect(status().isNoContent());
		verify(identidade).definirAtiva(t.emissor(), "sub-temporario@usuarios.teste", true);

		mvc.perform(put("/api/v1/usuarios/{id}/situacao", admin).contentType(MediaType.APPLICATION_JSON)
			.content("{\"ativo\":false}")
			.with(token("sub-admin")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail", is("Você não pode desativar o próprio usuário.")));
		mvc.perform(put("/api/v1/usuarios/{id}/concessoes", admin).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}""".formatted(perfilLeitura, t.grupo()))
			.with(token("sub-admin"))).andExpect(status().isConflict());
	}

	@Test
	void exigePermissaoEEscopoNoTenantInteiro() throws Exception {
		mvc.perform(get("/api/v1/usuarios").with(token("sub-admin-cidade"))).andExpect(status().isForbidden());
		var outro = DadosDeTeste.criarTenant("Outro tenant");
		var deOutroTenant = DadosDeTeste.criarUsuario(outro, "sub-outro", "outro@usuarios.teste", outro.grupo());
		mvc.perform(get("/api/v1/usuarios/{id}", deOutroTenant).with(token("sub-admin")))
			.andExpect(status().isNotFound());
	}

	private UUID convidar(String email, UUID perfil, UUID unidade) throws Exception {
		var criado = mvc
			.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nome":"Pessoa Convidada","email":"%s","concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}"""
					.formatted(email, perfil, unidade))
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(criado.getResponse().getContentAsString()).get("id").asString());
	}

	private static java.util.List<String> eventos(UUID usuario) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT tipo FROM evento_auditoria WHERE recurso = ? ORDER BY 1")) {
			consulta.setString(1, "usuario/" + usuario);
			try (var r = consulta.executeQuery()) {
				var tipos = new java.util.ArrayList<String>();
				while (r.next()) {
					tipos.add(r.getString(1));
				}
				return tipos;
			}
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
