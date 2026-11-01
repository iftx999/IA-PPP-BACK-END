package dev.aurora.acesso;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.model.EventoDeAcesso;
import dev.aurora.acesso.model.EventoDeAcesso.Tipo;
import dev.aurora.acesso.service.ProvedorIdentidade;
import dev.aurora.acesso.service.TrilhaDeAcessoService;
import dev.aurora.auditoria.VerificacaoDaTrilha;
import dev.aurora.contexto.ContextoAcesso;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trilha de acesso (doc 08, 8.6 e 8.7; ADR 0028): eventos do provedor de identidade na
 * auditoria do tenant e "sair de todos os dispositivos".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrilhaDeAcessoApiTest {

	private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

	private static Tenant t;

	private static Tenant outro;

	private static UUID admin;

	private static UUID leitor;

	@Autowired
	MockMvc mvc;

	@Autowired
	TrilhaDeAcessoService trilha;

	@Autowired
	VerificacaoDaTrilha verificacao;

	@MockitoBean
	ProvedorIdentidade identidade;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Trilha de acesso");
		outro = DadosDeTeste.criarTenant("Outro tenant");
		admin = DadosDeTeste.criarUsuario(t, "sub-admin", "admin@acesso.teste", t.grupo());
		leitor = DadosDeTeste.criarUsuario(t, "sub-leitor", "leitor@acesso.teste", t.cidadeA(),
				DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler"));
	}

	@BeforeEach
	void limpar() {
		reset(identidade);
	}

	@Test
	void importaLoginFalhaBloqueioELogoutUmaVezSo() throws Exception {
		var eventos = List.of(
				new EventoDeAcesso("e1", AGORA.minusSeconds(50), Tipo.FALHA_DE_LOGIN, "sub-admin", "10.0.0.7",
						"invalid_user_credentials", "aurora-web", null),
				new EventoDeAcesso("e2", AGORA.minusSeconds(40), Tipo.BLOQUEIO_TEMPORARIO, "sub-admin", "10.0.0.7",
						null, "aurora-web", null),
				new EventoDeAcesso("e3", AGORA.minusSeconds(30), Tipo.LOGIN, "sub-admin", "2001:db8::1", null,
						"aurora-web", "corporativo"),
				new EventoDeAcesso("e4", AGORA.minusSeconds(20), Tipo.LOGOUT, "sub-admin", "lixo", null, "aurora-web",
						null));
		when(identidade.eventosDeAcesso(eq(t.emissor()), isNull(), anyInt())).thenReturn(eventos);

		assertThat(importar(t)).isEqualTo(4);

		var gravados = trilhaDe(t, "usuario/" + admin);
		assertThat(gravados).extracting(Gravado::tipo)
			.containsExactly("LOGIN_FALHOU", "CONTA_BLOQUEADA", "LOGIN", "LOGOUT");
		assertThat(gravados).extracting(Gravado::usuario).containsOnly(admin);
		assertThat(gravados.get(0).detalhes()).contains("invalid_user_credentials");
		assertThat(gravados.get(1).detalhes()).contains("TEMPORARIO");
		assertThat(gravados.get(2).detalhes()).contains("corporativo");
		assertThat(gravados).extracting(Gravado::ip).containsExactly("10.0.0.7", "10.0.0.7", "2001:db8::1", null);
		assertThat(gravados.get(2).quando()).isEqualTo(AGORA.minusSeconds(30));

		// Segunda leitura: a margem relê os mesmos eventos, que não entram de novo.
		when(identidade.eventosDeAcesso(eq(t.emissor()), any(Instant.class), anyInt())).thenReturn(eventos);
		assertThat(importar(t)).isZero();
		verify(identidade).eventosDeAcesso(t.emissor(), AGORA.minusSeconds(20).minusSeconds(300), 2000);
		assertThat(trilhaDe(t, "usuario/" + admin)).hasSize(4);
		assertThat(ContextoAcesso.executar(sistema(t), verificacao::primeiraQuebra)).isNull();
	}

	@Test
	void falhaComContaDesconhecidaNaoGuardaOQueFoiDigitado() throws Exception {
		when(identidade.eventosDeAcesso(eq(outro.emissor()), any(), anyInt())).thenReturn(List.of(
				new EventoDeAcesso("x1", AGORA, Tipo.FALHA_DE_LOGIN, null, "10.0.0.9", "user_not_found", "aurora-web",
						null),
				new EventoDeAcesso("x2", AGORA, Tipo.LOGIN, "sub-de-outra-aplicacao", null, null, "outra", null)));

		assertThat(importar(outro)).isEqualTo(2);

		var gravados = trilhaDe(outro, "acesso/conta-desconhecida");
		assertThat(gravados).extracting(Gravado::tipo).containsExactly("LOGIN_FALHOU", "LOGIN");
		assertThat(gravados).extracting(Gravado::usuario).containsOnlyNulls();
		assertThat(gravados.get(0).detalhes()).contains("user_not_found").doesNotContain("@");
		assertThat(gravados.get(1).detalhes()).contains("sub-de-outra-aplicacao");
		// Nada vazou para o outro tenant.
		assertThat(trilhaDe(t, "acesso/conta-desconhecida")).isEmpty();
	}

	@Test
	void pessoaEncerraAsPropriasSessoes() throws Exception {
		mvc.perform(delete("/api/v1/eu/sessoes").with(token("sub-leitor"))).andExpect(status().isNoContent());
		verify(identidade).encerrarSessoes(t.emissor(), "sub-leitor");
		assertThat(trilhaDe(t, "usuario/" + leitor)).extracting(Gravado::tipo).contains("SESSOES_ENCERRADAS");
	}

	@Test
	void administradorEncerraAsSessoesDeOutraPessoa() throws Exception {
		mvc.perform(delete("/api/v1/usuarios/{id}/sessoes", leitor).with(token("sub-admin")))
			.andExpect(status().isNoContent());
		verify(identidade).encerrarSessoes(t.emissor(), "sub-leitor");
	}

	@Test
	void semPermissaoNaoEncerraSessoesDeOutros() throws Exception {
		mvc.perform(delete("/api/v1/usuarios/{id}/sessoes", admin).with(token("sub-leitor")))
			.andExpect(status().isForbidden());
		verify(identidade, never()).encerrarSessoes(anyString(), anyString());
	}

	@Test
	void usuarioDeOutroTenantNaoEEncontrado() throws Exception {
		var deFora = DadosDeTeste.criarUsuario(outro, "sub-fora", "fora@acesso.teste", outro.grupo());
		mvc.perform(delete("/api/v1/usuarios/{id}/sessoes", deFora).with(token("sub-admin")))
			.andExpect(status().isNotFound());
		verify(identidade, never()).encerrarSessoes(anyString(), anyString());
	}

	private int importar(Tenant tenant) throws Exception {
		return ContextoAcesso.executar(sistema(tenant), trilha::importar);
	}

	private static ContextoAcesso sistema(Tenant tenant) {
		return ContextoAcesso.somenteTenant(tenant.id());
	}

	private RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

	record Gravado(String tipo, UUID usuario, String ip, String detalhes, Instant quando) {
	}

	private static List<Gravado> trilhaDe(Tenant tenant, String recurso) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.prepareStatement("""
				SELECT tipo, usuario_id, host(ip_origem), detalhes::text, ocorrido_em FROM evento_auditoria
				 WHERE tenant_id = ? AND recurso = ? ORDER BY sequencia""")) {
			consulta.setObject(1, tenant.id());
			consulta.setString(2, recurso);
			try (var r = consulta.executeQuery()) {
				var lista = new ArrayList<Gravado>();
				while (r.next()) {
					lista.add(new Gravado(r.getString(1), r.getObject(2, UUID.class), r.getString(3), r.getString(4),
							r.getTimestamp(5).toInstant()));
				}
				return lista;
			}
		}
	}

}
