package dev.aurora.acesso;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.acesso.model.ProvedorCorporativo.Protocolo;
import dev.aurora.acesso.service.ProvedorIdentidade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSO corporativo (S10, ADR 0025) com as regras de produção: endereço HTTPS e público,
 * segundo fator obrigatório para administradores.
 */
@SpringBootTest(properties = { "aurora.seguranca.segundo-fator-administradores=true",
		"aurora.seguranca.sso-permitir-enderecos-internos=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SsoCorporativoApiTest {

	/** IP literal público: dispensa DNS no teste. */
	private static final String DESCOBERTA = "https://20.190.128.10/empresa/v2.0/.well-known/openid-configuration";

	private static final List<String> SENHA_E_CODIGO = List.of("pwd", "otp");

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ProvedorIdentidade identidade;

	/** O que está "gravado" no Keycloak simulado. */
	private final AtomicReference<ProvedorCorporativo> gravado = new AtomicReference<>();

	private long login = Instant.parse("2026-10-31T12:00:00Z").getEpochSecond();

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("SSO corporativo");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@sso.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-leitor", "leitor@sso.teste", t.cidadeA(),
				DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler"));
	}

	@BeforeEach
	void provedor() {
		reset(identidade);
		gravado.set(null);
		when(identidade.segundoFatorCadastradoEm(anyString(), anyString())).thenReturn(Optional.empty());
		when(identidade.provedorCorporativo(t.emissor())).thenAnswer(i -> Optional.ofNullable(gravado.get()));
		doAnswer(i -> {
			gravado.set(i.getArgument(1));
			return null;
		}).when(identidade).salvarProvedorCorporativo(eq(t.emissor()), any(), any());
		doAnswer(i -> {
			gravado.set(null);
			return null;
		}).when(identidade).removerProvedorCorporativo(t.emissor());
		login += 1000 + (long) (Math.random() * 1000);
	}

	@Test
	void semSsoMostraOsEnderecosParaRegistrarNoIdp() throws Exception {
		mvc.perform(get("/api/v1/seguranca/sso").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provedor", nullValue()))
			.andExpect(jsonPath("$.confiaSegundoFator", is(false)))
			.andExpect(jsonPath("$.enderecoRetorno", is(t.emissor() + "/broker/corporativo/endpoint")))
			.andExpect(jsonPath("$.entidadeSaml", is(t.emissor())))
			.andExpect(jsonPath("$.metadadosSaml", is(t.emissor() + "/broker/corporativo/endpoint/descriptor")));
		mvc.perform(get("/api/v1/seguranca/sso").with(token("sub-leitor", SENHA_E_CODIGO, null)))
			.andExpect(status().isForbidden());
	}

	@Test
	void configuraOidcSemDevolverOSegredoEAuditaSemDadosSensiveis() throws Exception {
		salvar("""
				{"protocolo":"OIDC","nome":" Entra ID ","endereco":"%s","clienteId":"app-aurora","segredo":"s3gr3do",
				 "habilitado":true,"confiaSegundoFator":false}""".formatted(DESCOBERTA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.provedor.nome", is("Entra ID")))
			.andExpect(jsonPath("$.provedor.clienteId", is("app-aurora")))
			.andExpect(jsonPath("$.provedor.segredo").doesNotExist());
		verify(identidade).salvarProvedorCorporativo(t.emissor(),
				new ProvedorCorporativo(Protocolo.OIDC, "Entra ID", DESCOBERTA, "app-aurora", true), "s3gr3do");

		// Atualizar sem segredo mantém o atual.
		salvar("""
				{"protocolo":"OIDC","nome":"Entra ID","endereco":"%s","clienteId":"app-aurora","segredo":"",
				 "habilitado":false,"confiaSegundoFator":false}""".formatted(DESCOBERTA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.provedor.habilitado", is(false)));
		verify(identidade).salvarProvedorCorporativo(eq(t.emissor()), any(), isNull());

		var detalhes = detalhesDosEventos();
		assertThat(detalhes).hasSize(2).noneMatch(d -> d.contains("s3gr3do") || d.contains("20.190"));
		assertThat(detalhes.get(0)).contains("\"novo\": true").contains("\"segredoAlterado\": true");
		assertThat(detalhes.get(1)).contains("\"novo\": false").contains("\"segredoAlterado\": false");

		mvc.perform(delete("/api/v1/seguranca/sso").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provedor", nullValue()));
		verify(identidade).removerProvedorCorporativo(t.emissor());
	}

	@Test
	void recusaConfiguracaoIncompletaOuEnderecoInseguro() throws Exception {
		salvar("""
				{"protocolo":"OIDC","nome":"X","endereco":"%s","clienteId":"app","habilitado":true,
				 "confiaSegundoFator":false}""".formatted(DESCOBERTA)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", containsString("segredo")));
		salvar("""
				{"protocolo":"OIDC","nome":"X","endereco":"%s","segredo":"s","habilitado":true,
				 "confiaSegundoFator":false}""".formatted(DESCOBERTA)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", containsString("client ID")));
		for (var endereco : List.of("http://20.190.128.10/x", "https://127.0.0.1/x", "https://10.1.2.3/x",
				"https://169.254.169.254/latest", "https://[::1]/x", "https://100.64.0.1/x", "ftp://x/y",
				"https://usuario:senha@20.190.128.10/x")) {
			salvar("""
					{"protocolo":"SAML","nome":"X","endereco":"%s","habilitado":true,"confiaSegundoFator":false}"""
				.formatted(endereco)).andExpect(status().isUnprocessableContent());
		}
		verify(identidade, never()).salvarProvedorCorporativo(anyString(), any(), any());
	}

	@Test
	void loginPeloIdpDispensaOCodigoSoQuandoOTenantConfiaNele() throws Exception {
		var pelaEmpresa = token("sub-admin", List.of(), ProvedorCorporativo.APELIDO);
		mvc.perform(get("/api/v1/eu").with(pelaEmpresa))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo", is("SEGUNDO_FATOR_NECESSARIO")));

		salvar("""
				{"protocolo":"SAML","nome":"ADFS","endereco":"https://20.190.128.10/metadata","habilitado":true,
				 "confiaSegundoFator":true}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.confiaSegundoFator", is(true)));
		try {
			mvc.perform(get("/api/v1/eu").with(token("sub-admin", List.of(), ProvedorCorporativo.APELIDO)))
				.andExpect(status().isOk());
			// Login com senha continua precisando do código.
			mvc.perform(get("/api/v1/eu").with(token("sub-admin", List.of("pwd"), null)))
				.andExpect(status().isForbidden());
		}
		finally {
			mvc.perform(delete("/api/v1/seguranca/sso").with(admin())).andExpect(status().isOk());
		}
		// Sem SSO a confiança é desligada junto.
		mvc.perform(get("/api/v1/seguranca/sso").with(admin())).andExpect(jsonPath("$.confiaSegundoFator", is(false)));
		mvc.perform(get("/api/v1/eu").with(token("sub-admin", List.of(), ProvedorCorporativo.APELIDO)))
			.andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.ResultActions salvar(String json) throws Exception {
		return mvc
			.perform(put("/api/v1/seguranca/sso").contentType(MediaType.APPLICATION_JSON).content(json).with(admin()));
	}

	private RequestPostProcessor admin() {
		return token("sub-admin", SENHA_E_CODIGO, null);
	}

	private RequestPostProcessor token(String sub, List<String> metodos, String idp) {
		login++;
		return jwt().jwt(j -> {
			j.issuer(t.emissor()).subject(sub).claim("amr", metodos).claim("auth_time", login);
			if (idp != null) {
				j.claim("idp", idp);
			}
		});
	}

	private static List<String> detalhesDosEventos() throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT detalhes::text FROM evento_auditoria "
						+ "WHERE recurso = ? AND tipo = 'SSO_CONFIGURADO' AND detalhes->>'protocolo' = 'OIDC' ORDER BY sequencia")) {
			consulta.setString(1, "tenant/" + t.id());
			try (var r = consulta.executeQuery()) {
				var lista = new java.util.ArrayList<String>();
				while (r.next()) {
					lista.add(r.getString(1));
				}
				return lista;
			}
		}
	}

}
