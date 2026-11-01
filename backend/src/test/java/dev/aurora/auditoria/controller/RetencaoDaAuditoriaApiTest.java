package dev.aurora.auditoria.controller;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.auditoria.service.AuditoriaService;
import dev.aurora.auditoria.service.RetencaoAuditoriaService;
import dev.aurora.contexto.ContextoAcesso;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Retenção da trilha de auditoria (doc 08, 8.7; ADR 0029). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RetencaoDaAuditoriaApiTest {

	private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	private static Tenant t;

	private static Tenant vizinho;

	@Autowired
	MockMvc mvc;

	@Autowired
	AuditoriaService auditoria;

	@Autowired
	RetencaoAuditoriaService retencao;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Retenção");
		vizinho = DadosDeTeste.criarTenant("Retenção vizinho");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@retencao.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-auditor", "auditor@retencao.teste", t.grupo(),
				DadosDeTeste.criarPerfil(t, "Auditor", "auditoria:ler"));
	}

	@Test
	void expurgaSoOComecoVencidoDaCadeiaEAVerificacaoContinuaIntegra() throws Exception {
		// Ordem da cadeia x horário: o 4º evento é antigo, mas vem depois de um recente
		// (ex.: login importado com o horário original) e por isso fica.
		registrar(t, AGORA.minus(900, ChronoUnit.DAYS));
		registrar(t, AGORA.minus(600, ChronoUnit.DAYS));
		registrar(t, AGORA.minus(30, ChronoUnit.DAYS));
		registrar(t, AGORA.minus(500, ChronoUnit.DAYS));
		registrar(t, AGORA);
		registrar(vizinho, AGORA.minus(900, ChronoUnit.DAYS));

		mvc.perform(put("/api/v1/seguranca/retencao-auditoria").with(token("sub-admin"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"meses\":12}")).andExpect(status().isOk()).andExpect(jsonPath("$.meses", is(12)));

		var apagados = ContextoAcesso.executar(sistema(t), retencao::expurgarVencidos);

		assertThat(apagados).isEqualTo(2);
		assertThat(tipos(t)).containsExactly("RELATORIO_EXPORTADO", "RELATORIO_EXPORTADO", "RELATORIO_EXPORTADO",
				"RETENCAO_AUDITORIA_ALTERADA", "TRILHA_EXPURGADA");
		var verificacao = ContextoAcesso.executar(sistema(t), auditoria::verificar);
		assertThat(verificacao.integra()).isTrue();
		assertThat(verificacao.expurgados()).isEqualTo(2);
		// O vizinho tem o prazo padrão (60 meses) e não é afetado.
		assertThat(tipos(vizinho)).hasSize(1);
		// Rodar de novo não apaga mais nada.
		assertThat(ContextoAcesso.executar(sistema(t), retencao::expurgarVencidos)).isZero();

		// Apagar o primeiro evento que ficou quebra a âncora deixada pelo expurgo.
		try (var c = BancoDeTeste.comoSuperusuario(); var apagar = c.prepareStatement("""
				DELETE FROM evento_auditoria WHERE tenant_id = ?
				   AND sequencia = (SELECT min(sequencia) FROM evento_auditoria WHERE tenant_id = ?)""")) {
			apagar.setObject(1, t.id());
			apagar.setObject(2, t.id());
			apagar.executeUpdate();
		}
		assertThat(ContextoAcesso.executar(sistema(t), auditoria::verificar).integra()).isFalse();
	}

	@Test
	void auditorConsultaMasSoQuemGereAcessoAltera() throws Exception {
		mvc.perform(get("/api/v1/auditoria/retencao").with(token("sub-auditor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minimo", is(12)))
			.andExpect(jsonPath("$.maximo", is(240)));
		// Segregação de funções: o Auditor não muda a política.
		mvc.perform(get("/api/v1/seguranca/retencao-auditoria").with(token("sub-auditor")))
			.andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/seguranca/retencao-auditoria").with(token("sub-auditor"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"meses\":24}")).andExpect(status().isForbidden());
	}

	@Test
	void prazoForaDosLimitesERecusado() throws Exception {
		mvc.perform(put("/api/v1/seguranca/retencao-auditoria").with(token("sub-admin"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"meses\":6}")).andExpect(status().is4xxClientError());
		mvc.perform(put("/api/v1/seguranca/retencao-auditoria").with(token("sub-admin"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"meses\":241}")).andExpect(status().is4xxClientError());
	}

	@Test
	void aplicacaoNaoApagaATrilhaNemBaixaOPrazoDireto() throws Exception {
		try (var c = BancoDeTeste.comoAplicacao(); var s = c.createStatement()) {
			s.execute("SELECT set_config('app.tenant_id', '" + t.id() + "', false)");
			assertThatThrownBy(() -> s.executeUpdate("DELETE FROM evento_auditoria")).isInstanceOf(SQLException.class)
				.hasMessageContaining("permission denied");
			assertThatThrownBy(() -> s.executeUpdate("UPDATE tenant SET auditoria_retencao_meses = 1"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("auditoria_retencao_meses");
		}
	}

	@Test
	void particoesDosProximosAnosSaoCriadasUmaVez() throws Exception {
		retencao.prepararParticoes();
		assertThat(retencao.prepararParticoes()).isZero();
		var ano = AGORA.atZone(java.time.ZoneId.of("America/Sao_Paulo")).getYear();
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
			consulta.setString(1, "evento_auditoria_" + (ano + 2));
			try (var r = consulta.executeQuery()) {
				r.next();
				assertThat(r.getBoolean(1)).isTrue();
			}
		}
	}

	private void registrar(Tenant tenant, Instant quando) throws Exception {
		ContextoAcesso.executar(sistema(tenant), () -> {
			auditoria.registrarOcorrido(TipoEvento.RELATORIO_EXPORTADO, null, quando, null, "teste/retencao",
					Map.of("quando", quando.toString()));
			return null;
		});
	}

	private static ContextoAcesso sistema(Tenant tenant) {
		return ContextoAcesso.somenteTenant(tenant.id());
	}

	private RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

	private static List<String> tipos(Tenant tenant) throws SQLException {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c
					.prepareStatement("SELECT tipo FROM evento_auditoria WHERE tenant_id = ? ORDER BY sequencia")) {
			consulta.setObject(1, tenant.id());
			try (var r = consulta.executeQuery()) {
				var lista = new ArrayList<String>();
				while (r.next()) {
					lista.add(r.getString(1));
				}
				return lista;
			}
		}
	}

}
