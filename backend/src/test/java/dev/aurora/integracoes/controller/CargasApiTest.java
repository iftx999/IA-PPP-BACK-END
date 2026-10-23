package dev.aurora.integracoes.controller;

import java.math.BigDecimal;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aurora.agente.limite-por-minuto=8")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CargasApiTest {

	private static Tenant t;

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
		t = DadosDeTeste.criarTenant("Cargas");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@cargas.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade", "cidade@cargas.teste", t.cidadeA());
	}

	@Test
	void agenteCarregaRecalculaAdesaoEResolveAtraso() throws Exception {
		var fonte = criarFonteAtrasada();
		var chave = gerarCredencial(fonte);
		assertThat(ContextoAcesso.executar(contextos.doTenant(t.id()).orElseThrow(), avaliador::avaliarIndicadores)
			.abertos()).isGreaterThanOrEqualTo(1);

		mvc.perform(get("/api/agente/v1/situacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fonte", is(fonte.toString())));

		// 3 ligados e 1 factível no setor A1, 1 cortado e 1 sem rede na Cidade B, 1
		// recusado.
		carga(chave, "2026-09",
				"""
						{"matricula":"S1","unidade":"%1$s","situacao":"LIGADO"},{"matricula":"S2","unidade":"%1$s","situacao":"LIGADO"},
						{"matricula":"S3","unidade":"%1$s","situacao":"ligado"},{"matricula":"S4","unidade":"%1$s","situacao":"FACTIVEL"},
						{"matricula":"B1","unidade":"%2$s","situacao":"CORTADO"},{"matricula":"B2","unidade":"%2$s","situacao":"POTENCIAL"},
						{"matricula":"X1","unidade":"NAO-EXISTE","situacao":"LIGADO"}"""
					.formatted(t.setorA1(), t.cidadeB()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.novos", is(6)))
			.andExpect(jsonPath("$.rejeitados", is(1)))
			.andExpect(jsonPath("$.rejeicoes[0].linha", is(7)));

		// Concessão: 3 ligados de 5 com rede (o sem rede fica fora do denominador).
		assertThat(adesao(t.concessao(), "2026-09-01")).isEqualByComparingTo("60");
		assertThat(adesao(t.setorA1(), "2026-09-01")).isEqualByComparingTo("75");
		assertThat(contar(
				"SELECT count(*) FROM alerta WHERE tenant_id = '%s' AND chave = 'FONTE:%s' AND resolvido_em IS NOT NULL"
					.formatted(t.id(), fonte)))
			.isEqualTo(1);

		// Mesma carga de novo: nada muda; uma situação nova vira atualização.
		carga(chave, "2026-09",
				"""
						{"matricula":"S1","unidade":"%1$s","situacao":"LIGADO"},{"matricula":"S4","unidade":"%1$s","situacao":"LIGADO"}"""
					.formatted(t.setorA1()))
			.andExpect(jsonPath("$.inalterados", is(1)))
			.andExpect(jsonPath("$.atualizados", is(1)));
		assertThat(adesao(t.setorA1(), "2026-09-01")).isEqualByComparingTo("100");

		carga(chave, "2099-01", """
				{"matricula":"S1","unidade":"%s","situacao":"LIGADO"}""".formatted(t.setorA1()))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/integracoes/fontes/{id}/execucoes", fonte).with(token("sub-admin")))
			.andExpect(jsonPath("$[0].situacao", is("SUCESSO")))
			.andExpect(jsonPath("$[0].origem", is("AGENTE")));
	}

	@Test
	void credencialErradaRevogadaELimiteDeTaxa() throws Exception {
		var fonte = criarFonte();
		var chave = gerarCredencial(fonte);
		mvc.perform(get("/api/agente/v1/situacao")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/agente/v1/situacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave + "x"))
			.andExpect(status().isUnauthorized());
		// Token OIDC de usuário não serve na API do agente.
		mvc.perform(get("/api/agente/v1/situacao").with(token("sub-admin"))).andExpect(status().isUnauthorized());

		for (int i = 0; i < 8; i++) {
			mvc.perform(get("/api/agente/v1/situacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave))
				.andExpect(status().isOk());
		}
		mvc.perform(get("/api/agente/v1/situacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave))
			.andExpect(status().isTooManyRequests());

		var prefixo = chave.substring(0, chave.indexOf('.'));
		mvc.perform(post("/api/v1/integracoes/credenciais/{p}/revogacao", prefixo).with(token("sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/agente/v1/situacao").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void cargaPorArquivoNaTela() throws Exception {
		var fonte = criarFonte();
		var csv = "matricula;unidade;situacao\nF1;%s;LIGADO\nF2;%s;Factível\n".formatted(t.cidadeB(), t.cidadeB());
		var arquivo = new MockMultipartFile("arquivo", "carga.csv", "text/csv", csv.getBytes());
		mvc.perform(multipart("/api/v1/integracoes/fontes/{id}/cargas", fonte).file(arquivo)
			.param("competencia", "2026-07")
			.with(token("sub-admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.novos", is(2)));
		// Carga atualiza o cliente inteiro: quem atua só na Cidade A não envia.
		mvc.perform(multipart("/api/v1/integracoes/fontes/{id}/cargas", fonte).file(arquivo)
			.param("competencia", "2026-07")
			.with(token("sub-cidade"))).andExpect(status().isForbidden());
		mvc.perform(multipart("/api/v1/integracoes/fontes/{id}/cargas", fonte)
			.file(new MockMultipartFile("arquivo", "x.csv", "text/csv", "a;b\n1;2".getBytes()))
			.param("competencia", "2026-07")
			.with(token("sub-admin"))).andExpect(status().isUnprocessableContent());
	}

	@Test
	void agenteEnviaLocalizacoesSemSubstituirPontoDeCampo() throws Exception {
		var chave = gerarCredencial(criarFonte());
		executar("""
				INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
				VALUES ('%s', 'L-CAMPO', '%s', ST_SetSRID(ST_MakePoint(-47.5, -22.6), 4674), 'CAMPO', 'MANUAL')"""
			.formatted(t.id(), t.setorA1()));
		var registros = """
				{"matricula":"L-1","unidade":"%1$s","longitude":-47.51,"latitude":-22.61,"precisao":"ENDERECO"},
				{"matricula":"L-2","unidade":"%1$s","longitude":-47.52,"latitude":-22.62,"precisao":"bairro"},
				{"matricula":"L-CAMPO","unidade":"%1$s","longitude":-47.0,"latitude":-22.0,"precisao":"ENDERECO"},
				{"matricula":"L-3","unidade":"%1$s","longitude":10.0,"latitude":-22.6,"precisao":"ENDERECO"},
				{"matricula":"L-4","unidade":"%1$s","longitude":-47.5,"latitude":-22.6,"precisao":"CEP"}"""
			.formatted(t.setorA1());
		localizacoes(chave, registros).andExpect(status().isOk())
			.andExpect(jsonPath("$.lidos", is(5)))
			.andExpect(jsonPath("$.novos", is(2)))
			.andExpect(jsonPath("$.preservados", is(1)))
			.andExpect(jsonPath("$.rejeitados", is(2)))
			.andExpect(jsonPath("$.rejeicoes[0].linha", is(4)))
			.andExpect(
					jsonPath("$.rejeicoes[1].motivo", is("Precisão inválida (use ENDERECO, LOGRADOURO ou BAIRRO).")));
		assertThat(contar("""
				SELECT count(*) FROM imovel_localizacao WHERE tenant_id = '%s' AND codigo_origem = 'L-CAMPO'
				   AND origem = 'CAMPO' AND ST_X(geom) = -47.5""".formatted(t.id()))).isEqualTo(1);
		assertThat(contar("""
				SELECT count(*) FROM imovel_localizacao WHERE tenant_id = '%s' AND codigo_origem = 'L-2'
				   AND precisao = 'BAIRRO'""".formatted(t.id()))).isEqualTo(1);

		// Reenvio igual não muda nada; ponto novo vira atualização.
		localizacoes(chave,
				"""
						{"matricula":"L-1","unidade":"%1$s","longitude":-47.51,"latitude":-22.61,"precisao":"ENDERECO"},
						{"matricula":"L-2","unidade":"%1$s","longitude":-47.53,"latitude":-22.62,"precisao":"ENDERECO"}"""
					.formatted(t.setorA1()))
			.andExpect(jsonPath("$.inalterados", is(1)))
			.andExpect(jsonPath("$.atualizados", is(1)));
		assertThat(contar("""
				SELECT count(*) FROM evento_auditoria WHERE tenant_id = '%s' AND tipo = 'IMOVEIS_GEOCODIFICADOS'"""
			.formatted(t.id()))).isEqualTo(2);
		localizacoes(chave, """
				{"matricula":"L-9","unidade":"NAO-EXISTE","longitude":-47.5,"latitude":-22.6,"precisao":"ENDERECO"}""")
			.andExpect(status().isUnprocessableContent());
	}

	private ResultActions localizacoes(String chave, String registros) throws Exception {
		return mvc.perform(post("/api/agente/v1/localizacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"registros\":[%s]}".formatted(registros)));
	}

	private ResultActions carga(String chave, String competencia, String registros) throws Exception {
		return mvc.perform(post("/api/agente/v1/cargas").header(HttpHeaders.AUTHORIZATION, "Bearer " + chave)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"competencia\":\"%s\",\"registros\":[%s]}".formatted(competencia, registros)));
	}

	private UUID criarFonte() throws Exception {
		var resposta = mvc
			.perform(post("/api/v1/integracoes/fontes").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nome":"Fonte %s","tipo":"AGENTE","atrasoMaximoHoras":48,"ativo":true}"""
					.formatted(UUID.randomUUID().toString().substring(0, 8)))
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID criarFonteAtrasada() throws Exception {
		var fonte = criarFonte();
		executar("UPDATE fonte_dados SET criado_em = now() - interval '10 days' WHERE id = '%s'".formatted(fonte));
		return fonte;
	}

	private String gerarCredencial(UUID fonte) throws Exception {
		var resposta = mvc.perform(post("/api/v1/integracoes/fontes/{id}/credenciais", fonte).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(resposta.getResponse().getContentAsString()).get("chave").asString();
	}

	private static BigDecimal adesao(UUID unidade, String competencia) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement(
						"SELECT valor FROM medicao WHERE unidade_id = ?::uuid AND periodo_inicio = ?::date")) {
			consulta.setString(1, unidade.toString());
			consulta.setString(2, competencia);
			try (var r = consulta.executeQuery()) {
				assertThat(r.next()).as("medição de %s", unidade).isTrue();
				return r.getBigDecimal(1);
			}
		}
	}

	private static long contar(String sql) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.createStatement();
				var r = consulta.executeQuery(sql)) {
			r.next();
			return r.getLong(1);
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
