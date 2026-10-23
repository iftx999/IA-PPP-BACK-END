package dev.aurora.assistente.controller;

import dev.aurora.BancoDeTeste;
import dev.aurora.ClaudeSimulado;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assistente de ponta a ponta com a API do Claude simulada: as ferramentas leem o banco
 * de verdade (Cidade A: 4.954 ligados de 5.800 = 85,41%, meta 85%).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssistenteApiTest {

	private static final ClaudeSimulado CLAUDE = new ClaudeSimulado();

	private static Tenant t;

	private static Tenant limitado;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@DynamicPropertySource
	static void ia(DynamicPropertyRegistry registro) {
		registro.add("aurora.ia.chave", () -> "chave-de-teste");
		registro.add("aurora.ia.url-base", CLAUDE::url);
	}

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Assistente");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@assistente.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-colega", "colega@assistente.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade-b", "b@assistente.teste", t.cidadeB());
		var semAssistente = DadosDeTeste.criarPerfil(t, "Sem assistente", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-sem", "sem@assistente.teste", t.grupo(), semAssistente);

		limitado = DadosDeTeste.criarTenant("Assistente limitado");
		DadosDeTeste.criarUsuario(limitado, "sub-limitado", "admin@limitado.teste", limitado.grupo());
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("INSERT INTO assistente_limite (tenant_id, usd_por_mes) VALUES ('%s', 0.01)"
				.formatted(limitado.id()));
			s.execute("""
					INSERT INTO assistente_uso_mensal (tenant_id, mes, perguntas, custo_usd)
					VALUES ('%s', date_trunc('month', now())::date, 3, 0.02)""".formatted(limitado.id()));
		}
	}

	@AfterAll
	static void parar() {
		CLAUDE.close();
	}

	@Test
	void respondeComFontesELinhasDeDados() throws Exception {
		CLAUDE.responder(adesaoDaCidadeA(), ClaudeSimulado
			.texto("A Cidade A está com 85,41% [1] (4.954 de 5.800 imóveis), acima da meta de 85% [1]."));
		long antes = eventos("PERGUNTA_IA");

		var resposta = perguntar("sub-admin", null, "Como está a adesão da Cidade A?").andExpect(status().isOk())
			.andExpect(jsonPath("$.conversa.titulo", is("Como está a adesão da Cidade A?")))
			.andExpect(jsonPath("$.mensagens[0].papel", is("USUARIO")))
			.andExpect(jsonPath("$.mensagens[1].papel", is("ASSISTENTE")))
			.andExpect(jsonPath("$.mensagens[1].fontes[0].numero", is(1)))
			.andExpect(jsonPath("$.mensagens[1].fontes[0].titulo", is("Adesão à rede de esgoto")))
			.andExpect(jsonPath("$.mensagens[1].fontes[0].detalhe", is("MUNICIPIO " + t.cidadeA() + " · ago/2026")))
			.andExpect(jsonPath("$.mensagens[1].dados[0].campos.Adesão", is("85,41%")))
			.andExpect(jsonPath("$.mensagens[1].dados[0].campos.Meta", is("85,00%")))
			.andExpect(jsonPath("$.mensagens[1].dados[0].campos.Situação", is("Meta atingida")))
			.andExpect(jsonPath("$.mensagens[1].numerosSemFonte", hasSize(0)))
			.andReturn();
		assertThat(eventos("PERGUNTA_IA")).isEqualTo(antes + 1);

		// A ferramenta leu o banco; o modelo recebeu os números com a fonte.
		var resultado = CLAUDE.pedidos().get(1).get("messages").get(2).get("content").get(0);
		assertThat(resultado.get("is_error").asBoolean()).isFalse();
		assertThat(resultado.get("content").toString()).contains("\\\"fonte\\\":1")
			.contains("85.41")
			.contains("4954")
			.contains("Meta manual");

		// Segunda pergunta na mesma conversa leva o histórico.
		var conversa = json.readTree(resposta.getResponse().getContentAsString()).get("conversa").get("id").asString();
		CLAUDE.responder(ClaudeSimulado.texto("Não encontrei esse dado na base da Aurora."));
		perguntar("sub-admin", conversa, "E a projeção para 2030?").andExpect(status().isOk())
			.andExpect(jsonPath("$.conversa.id", is(conversa)))
			.andExpect(jsonPath("$.mensagens[1].fontes", hasSize(0)));
		assertThat(CLAUDE.pedidos().getFirst().get("messages")).hasSize(3);
		mvc.perform(get("/api/v1/assistente/conversas/{id}", conversa).with(token("sub-admin")))
			.andExpect(jsonPath("$.mensagens", hasSize(4)));
	}

	@Test
	void numeroQueNaoVeioDasFerramentasFicaMarcado() throws Exception {
		CLAUDE.responder(adesaoDaCidadeA(), ClaudeSimulado.texto("A Cidade A deve chegar a 91,7% [1] em 2027 [9]."));
		perguntar("sub-admin", null, "Projeção da Cidade A").andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagens[1].numerosSemFonte", contains("91,7", "2027")))
			// marca de fonte inexistente é removida
			.andExpect(jsonPath("$.mensagens[1].texto", is("A Cidade A deve chegar a 91,7% [1] em 2027 .")));
	}

	@Test
	void unidadeForaDoEscopoNaoChegaAoModelo() throws Exception {
		CLAUDE.responder(adesaoDaCidadeA(), ClaudeSimulado.texto("Não encontrei a Cidade A no seu escopo."));
		perguntar("sub-cidade-b", null, "Adesão da Cidade A?").andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagens[1].fontes", hasSize(0)));
		var resultado = CLAUDE.pedidos().get(1).get("messages").get(2).get("content").get(0);
		assertThat(resultado.get("is_error").asBoolean()).isTrue();
		assertThat(resultado.get("content").toString()).contains("não encontrada").doesNotContain("4954");
	}

	@Test
	void conversasSaoDeCadaPessoaEComBusca() throws Exception {
		CLAUDE.responder(ClaudeSimulado.texto("Não encontrei."));
		var resposta = perguntar("sub-colega", null, "Quais setores estão abaixo do alerta?").andReturn();
		var conversa = json.readTree(resposta.getResponse().getContentAsString()).get("conversa").get("id").asString();

		mvc.perform(get("/api/v1/assistente/conversas/{id}", conversa).with(token("sub-admin")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/assistente/conversas").param("busca", "setores").with(token("sub-colega")))
			.andExpect(jsonPath("$[*].id", contains(conversa)));
		mvc.perform(get("/api/v1/assistente/conversas").param("busca", "setores").with(token("sub-admin")))
			.andExpect(jsonPath("$", hasSize(0)));
		perguntar("sub-admin", conversa, "continua").andExpect(status().isNotFound());
	}

	@Test
	void avaliacaoComComentarioSoNaRespostaDoAssistente() throws Exception {
		CLAUDE.responder(ClaudeSimulado.texto("Não encontrei."));
		var resposta = json.readTree(
				perguntar("sub-admin", null, "Teste de avaliação").andReturn().getResponse().getContentAsString());
		var pergunta = resposta.get("mensagens").get(0).get("id").asString();
		var respostaId = resposta.get("mensagens").get(1).get("id").asString();

		avaliar(respostaId, "{\"avaliacao\": -1, \"comentario\": \"Faltou o setor Norte\"}")
			.andExpect(status().isNoContent());
		avaliar(pergunta, "{\"avaliacao\": 1}").andExpect(status().isNotFound());
		avaliar(respostaId, "{\"avaliacao\": 0}").andExpect(status().isBadRequest());
		try (var c = BancoDeTeste.comoSuperusuario();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT avaliacao, avaliacao_comentario FROM assistente_mensagem WHERE id = '"
						+ respostaId + "'")) {
			r.next();
			assertThat(r.getInt(1)).isEqualTo(-1);
			assertThat(r.getString(2)).isEqualTo("Faltou o setor Norte");
		}
	}

	@Test
	void recusaViraRespostaPadrao() throws Exception {
		CLAUDE.responder(ClaudeSimulado.recusa());
		perguntar("sub-admin", null, "Algo fora do tema").andExpect(status().isOk())
			.andExpect(jsonPath("$.mensagens[1].texto", is(
					"Não posso responder a essa pergunta. Reformule com foco nos indicadores de adesão, metas, unidades ou alertas.")));
	}

	@Test
	void limiteMensalPermissaoEEntradaInvalida() throws Exception {
		mvc.perform(post("/api/v1/assistente/perguntas").contentType(MediaType.APPLICATION_JSON)
			.content("{\"pergunta\": \"Oi\"}")
			.with(jwt().jwt(j -> j.issuer(limitado.emissor()).subject("sub-limitado"))))
			.andExpect(status().isTooManyRequests());
		perguntar("sub-sem", null, "Oi").andExpect(status().isForbidden());
		perguntar("sub-admin", null, " ").andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/assistente/situacao").with(token("sub-admin")))
			.andExpect(jsonPath("$.disponivel", is(true)));
	}

	private String adesaoDaCidadeA() {
		return ClaudeSimulado.ferramenta("consultar_adesao", """
				{"unidades": ["municipio %s"], "incluir_subordinadas": false, "competencia": null}"""
			.formatted(t.cidadeA()));
	}

	private ResultActions perguntar(String sub, String conversa, String pergunta) throws Exception {
		var corpo = json.writeValueAsString(conversa == null ? java.util.Map.of("pergunta", pergunta)
				: java.util.Map.of("pergunta", pergunta, "conversaId", conversa));
		return mvc.perform(post("/api/v1/assistente/perguntas").contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.with(token(sub)));
	}

	private ResultActions avaliar(String mensagem, String corpo) throws Exception {
		return mvc.perform(
				put("/api/v1/assistente/mensagens/{id}/avaliacao", mensagem).contentType(MediaType.APPLICATION_JSON)
					.content(corpo)
					.with(token("sub-admin")));
	}

	private static long eventos(String tipo) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c
					.prepareStatement("SELECT count(*) FROM evento_auditoria WHERE tenant_id = ?::uuid AND tipo = ?")) {
			consulta.setString(1, t.id().toString());
			consulta.setString(2, tipo);
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getLong(1);
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
