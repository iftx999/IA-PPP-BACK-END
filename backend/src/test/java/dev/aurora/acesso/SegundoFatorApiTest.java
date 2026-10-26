package dev.aurora.acesso;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
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
 * Verificação em duas etapas (H9, RN-17) com a obrigatoriedade para administradores
 * ligada.
 */
@SpringBootTest(properties = "aurora.seguranca.segundo-fator-administradores=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SegundoFatorApiTest {

	private static final List<String> SENHA_E_CODIGO = List.of("pwd", "otp");

	private static final List<String> SO_SENHA = List.of("pwd");

	private static Tenant t;

	private static UUID admin;

	private static UUID leitor;

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ProvedorIdentidade identidade;

	/** Cada teste usa logins diferentes: a conferência é guardada por login. */
	private long login = Instant.parse("2026-10-26T12:00:00Z").getEpochSecond();

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Segundo fator");
		admin = DadosDeTeste.criarUsuario(t, "sub-admin", "admin@mfa.teste", t.grupo());
		leitor = DadosDeTeste.criarUsuario(t, "sub-leitor", "leitor@mfa.teste", t.cidadeA(),
				DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler"));
	}

	@BeforeEach
	void provedor() {
		reset(identidade);
		when(identidade.segundoFatorCadastradoEm(anyString(), anyString())).thenReturn(Optional.empty());
		login += 1000 + (long) (Math.random() * 1000);
	}

	@Test
	void administradorSemCodigoPrecisaEntrarDeNovoECadastrar() throws Exception {
		mvc.perform(get("/api/v1/eu").with(token("sub-admin", SO_SENHA)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo", is("SEGUNDO_FATOR_NECESSARIO")));
		verify(identidade).exigirCadastroSegundoFator(t.emissor(), "sub-admin");

		mvc.perform(get("/api/v1/eu").with(token("sub-admin", SENHA_E_CODIGO))).andExpect(status().isOk());
	}

	@Test
	void loginEmQueOCodigoFoiCadastradoValeComoSegundoFator() throws Exception {
		when(identidade.segundoFatorCadastradoEm(t.emissor(), "sub-admin"))
			.thenReturn(Optional.of(Instant.ofEpochSecond(login).plusSeconds(40)));
		mvc.perform(get("/api/v1/eu").with(token("sub-admin", SO_SENHA))).andExpect(status().isOk());
		verify(identidade, never()).exigirCadastroSegundoFator(anyString(), anyString());
	}

	@Test
	void codigoCadastradoAntesDoLoginNaoSubstituiOCodigo() throws Exception {
		when(identidade.segundoFatorCadastradoEm(t.emissor(), "sub-admin"))
			.thenReturn(Optional.of(Instant.ofEpochSecond(login).minusSeconds(3600)));
		mvc.perform(get("/api/v1/eu").with(token("sub-admin", SO_SENHA))).andExpect(status().isForbidden());
		// Já tem o TOTP: o próximo login pede o código, não o cadastro.
		verify(identidade, never()).exigirCadastroSegundoFator(anyString(), anyString());
	}

	@Test
	void provedorIndisponivelRecusa() throws Exception {
		when(identidade.segundoFatorCadastradoEm(anyString(), anyString()))
			.thenThrow(new ServicoExternoIndisponivelException("fora do ar", null));
		mvc.perform(get("/api/v1/eu").with(token("sub-admin", SO_SENHA))).andExpect(status().isForbidden());
	}

	@Test
	void politicaDoTenantValeParaTodosNaProximaRequisicao() throws Exception {
		mvc.perform(get("/api/v1/eu").with(token("sub-leitor", SO_SENHA))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/seguranca/segundo-fator").with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.todos", is(false)))
			.andExpect(jsonPath("$.administradores", is(true)));
		mvc.perform(get("/api/v1/seguranca/segundo-fator").with(token("sub-leitor", SO_SENHA)))
			.andExpect(status().isForbidden());

		try {
			definirTodos(true);
			mvc.perform(get("/api/v1/eu").with(token("sub-leitor", SO_SENHA)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo", is("SEGUNDO_FATOR_NECESSARIO")));
			mvc.perform(get("/api/v1/eu").with(token("sub-leitor", SENHA_E_CODIGO))).andExpect(status().isOk());
			mvc.perform(get("/api/v1/usuarios/{id}/segundo-fator", leitor).with(token("sub-admin", SENHA_E_CODIGO)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exigido", is(true)))
				.andExpect(jsonPath("$.cadastrado", is(false)));
		}
		finally {
			definirTodos(false);
		}
		mvc.perform(get("/api/v1/usuarios/{id}/segundo-fator", leitor).with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(jsonPath("$.exigido", is(false)));
		assertThat(eventos("tenant/" + t.id())).containsExactly("POLITICA_SEGUNDO_FATOR_ALTERADA",
				"POLITICA_SEGUNDO_FATOR_ALTERADA");
	}

	@Test
	void administradorRemoveOSegundoFatorDeOutraPessoa() throws Exception {
		when(identidade.segundoFatorCadastradoEm(t.emissor(), "sub-leitor"))
			.thenReturn(Optional.of(Instant.parse("2026-10-01T10:00:00Z")));
		mvc.perform(get("/api/v1/usuarios/{id}/segundo-fator", leitor).with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(jsonPath("$.cadastrado", is(true)))
			.andExpect(jsonPath("$.cadastradoEm", is("2026-10-01T10:00:00Z")));

		mvc.perform(delete("/api/v1/usuarios/{id}/segundo-fator", leitor).with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(status().isNoContent());
		verify(identidade).removerSegundoFator(t.emissor(), "sub-leitor");
		// A política não exige do leitor: nada de cadastro obrigatório.
		verify(identidade, never()).exigirCadastroSegundoFator(t.emissor(), "sub-leitor");

		mvc.perform(delete("/api/v1/usuarios/{id}/segundo-fator", admin).with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(status().isConflict());
		assertThat(eventos("usuario/" + leitor)).contains("SEGUNDO_FATOR_REMOVIDO");
	}

	private void definirTodos(boolean todos) throws Exception {
		mvc.perform(put("/api/v1/seguranca/segundo-fator").contentType(MediaType.APPLICATION_JSON)
			.content("{\"todos\":" + todos + "}")
			.with(token("sub-admin", SENHA_E_CODIGO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.todos", is(todos)));
	}

	private RequestPostProcessor token(String sub, List<String> metodos) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub).claim("amr", metodos).claim("auth_time", login));
	}

	private static List<String> eventos(String recurso) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT tipo FROM evento_auditoria WHERE recurso = ? ORDER BY 1")) {
			consulta.setString(1, recurso);
			try (var r = consulta.executeQuery()) {
				var tipos = new java.util.ArrayList<String>();
				while (r.next()) {
					tipos.add(r.getString(1));
				}
				return tipos;
			}
		}
	}

}
