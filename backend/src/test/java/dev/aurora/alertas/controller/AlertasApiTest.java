package dev.aurora.alertas.controller;

import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.alertas.service.AvaliadorAlertas;
import dev.aurora.contexto.ContextoAcesso;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlertasApiTest {

	private static Tenant t;

	private static UUID fonteAtrasada;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	AvaliadorAlertas avaliador;

	@Autowired
	ContextosDoSistema contextos;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Alertas");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@alertas.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-cidade-a", "cidade-a@alertas.teste", t.cidadeA(), leitura);
		fonteAtrasada = UUID.randomUUID();
		executar("""
				INSERT INTO fonte_dados (id, tenant_id, nome, tipo_adaptador, atraso_maximo, criado_em)
				VALUES ('%s', '%s', 'Sistema comercial', 'AGENTE', interval '2 days', now() - interval '30 days')"""
			.formatted(fonteAtrasada, t.id()));
	}

	@Test
	void avaliacaoAbreNaoDuplicaEResolveSozinho() throws Exception {
		var primeira = avaliar();
		assertThat(primeira.abertos()).isEqualTo(3);
		assertThat(avaliar()).isEqualTo(new AvaliadorAlertas.Resultado(0, 0, 0));

		mvc.perform(get("/api/v1/alertas").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].severidade", is("CRITICO")))
			.andExpect(
					jsonPath("$[0].titulo", is("Adesão abaixo do limite de alerta em %s".formatted(nome(t.cidadeB())))))
			.andExpect(jsonPath("$[*].titulo", hasItem("Fonte de dados atrasada: Sistema comercial")));

		// Escopo: quem atua na Cidade A vê o alerta da fonte (do tenant), mas não o da
		// Cidade B.
		mvc.perform(get("/api/v1/alertas").with(token("sub-cidade-a")))
			.andExpect(jsonPath("$[*].tipo", hasItem("FONTE_ATRASADA")))
			.andExpect(jsonPath("$[*].unidadeId", not(hasItem(t.cidadeB().toString()))));

		// Nova carga concluída: o alerta da fonte se resolve na próxima avaliação.
		executar("""
				INSERT INTO execucao_sincronizacao (id, tenant_id, fonte_dados_id, inicio, fim, status)
				VALUES (gen_random_uuid(), '%s', '%s', now(), now(), 'SUCESSO')""".formatted(t.id(), fonteAtrasada));
		assertThat(avaliar().resolvidos()).isEqualTo(1);
		mvc.perform(get("/api/v1/alertas").param("estado", "RESOLVIDOS").with(token("sub-admin")))
			.andExpect(jsonPath("$[0].tipo", is("FONTE_ATRASADA")));
	}

	@Test
	void reconhecerEResumo() throws Exception {
		avaliar();
		var lista = json.readTree(mvc.perform(get("/api/v1/alertas").with(token("sub-admin")))
			.andReturn()
			.getResponse()
			.getContentAsString());
		var id = lista.get(0).get("id").asString();
		mvc.perform(post("/api/v1/alertas/{id}/reconhecimento", id).with(token("sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/alertas/{id}/reconhecimento", id).with(token("sub-admin")))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/alertas").param("estado", "RECONHECIDOS").with(token("sub-admin")))
			.andExpect(jsonPath("$[0].reconhecidoPor", is("admin@alertas.teste")));
		mvc.perform(get("/api/v1/alertas/resumo").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reconhecidos", is(1)))
			.andExpect(jsonPath("$.competencia", is("2026-08")));
	}

	@Test
	void avaliarAgoraExigeGestaoDeIndicadores() throws Exception {
		mvc.perform(post("/api/v1/alertas/avaliacao").with(token("sub-cidade-a"))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/alertas/avaliacao").with(token("sub-admin"))).andExpect(status().isOk());
	}

	@Test
	void trilhaAdulteradaGeraAlertaCritico() throws Exception {
		var outro = DadosDeTeste.criarTenant("Trilha");
		DadosDeTeste.criarUsuario(outro, "sub-trilha", "trilha@alertas.teste", outro.grupo());
		var contexto = contextos.doTenant(outro.id()).orElseThrow();
		// Gera eventos pela API (unidades criadas) e confere que a trilha está íntegra.
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/v1/unidades").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content("""
						{"tipo":"SETOR_COMERCIAL","unidadePaiId":"%s","dados":{"codigo":"T-%d","nome":"Setor %d"}}"""
					.formatted(outro.cidadeA(), i, i))
				.with(jwt().jwt(j -> j.issuer(outro.emissor()).subject("sub-trilha")))).andExpect(status().isCreated());
		}
		assertThat(ContextoAcesso.executar(contexto, avaliador::verificarTrilha).abertos()).isZero();

		executar(
				"UPDATE evento_auditoria SET detalhes = '{\"codigo\":\"ADULTERADO\"}' WHERE tenant_id = '%s' AND sequencia = 1"
					.formatted(outro.id()));
		assertThat(ContextoAcesso.executar(contexto, avaliador::verificarTrilha).abertos()).isEqualTo(1);
		mvc.perform(get("/api/v1/alertas").with(jwt().jwt(j -> j.issuer(outro.emissor()).subject("sub-trilha"))))
			.andExpect(jsonPath("$[0].tipo", is("TRILHA_ALTERADA")))
			.andExpect(jsonPath("$[0].severidade", is("CRITICO")));
	}

	private AvaliadorAlertas.Resultado avaliar() throws Exception {
		return ContextoAcesso.executar(contextos.doTenant(t.id()).orElseThrow(), avaliador::avaliarIndicadores);
	}

	private static String nome(UUID unidade) {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT nome FROM unidade WHERE id = ?::uuid")) {
			consulta.setString(1, unidade.toString());
			try (var r = consulta.executeQuery()) {
				r.next();
				return r.getString(1);
			}
		}
		catch (java.sql.SQLException erro) {
			throw new IllegalStateException(erro);
		}
	}

	private static void executar(String sql) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario(); var comando = c.createStatement()) {
			comando.execute(sql);
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
