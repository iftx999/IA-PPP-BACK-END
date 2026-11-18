package dev.aurora.campanhas.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0036 (I2–I4) e 0044: campanha a partir dos sem ligação, OS do catálogo priorizadas
 * pelo semáforo, programação para a equipe, encerramento e devolução, conferência pela
 * carga, alerta de divergência e encerramento da campanha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CampanhasApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static Tenant t;

	private static UUID campo;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Campanhas");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@campanhas.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor", "campanha:gerenciar", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-gestor", "gestor@campanhas.teste", t.cidadeA(), gestor);
		var equipeDeCampo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar", "dados_pessoais:ler");
		campo = DadosDeTeste.criarUsuario(t, "sub-campo", "campo@campanhas.teste", t.concessao(), equipeDeCampo);
		DadosDeTeste.criarUsuario(t, "sub-outro", "outro@campanhas.teste", t.concessao(), equipeDeCampo);
	}

	@Test
	void campanhaDaPreviaAoEncerramento() throws Exception {
		var hoje = LocalDate.now(FUSO);
		var recorte = """
				{"origem":"SEM_LIGACAO","unidadeId":"%s"}""".formatted(t.concessao());

		// Prévia: 5 no setor A1 (meta atingida → baixa) e B-001 na Cidade B (abaixo do
		// alerta → alta). Concessão: 8.761 de 11.400; meta 80% = 9.120 → faltam 359.
		postar("/api/v1/campanhas/previa", recorte, "sub-admin").andExpect(status().isOk())
			.andExpect(jsonPath("$.competencia").value("2026-08"))
			.andExpect(jsonPath("$.imoveis").value(6))
			.andExpect(jsonPath("$.factiveis").value(5))
			.andExpect(jsonPath("$.cortados").value(1))
			.andExpect(jsonPath("$.alta").value(1))
			.andExpect(jsonPath("$.baixa").value(5))
			.andExpect(jsonPath("$.impacto.adesao").value(76.85))
			.andExpect(jsonPath("$.impacto.adesaoSeTodasLigarem").value(76.90))
			.andExpect(jsonPath("$.impacto.faltamParaMeta").value(359));
		postar("/api/v1/campanhas/previa", """
				{"origem":"SETOR","unidadeId":"%s"}""".formatted(t.cidadeA()), "sub-admin")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/campanhas/previa", """
				{"origem":"AREA_DO_MAPA","unidadeId":"%s"}""".formatted(t.concessao()), "sub-admin")
			.andExpect(status().isUnprocessableContent());
		// Fora da área de quem gerencia só a Cidade A; sem a permissão, 403.
		postar("/api/v1/campanhas/previa", recorte, "sub-gestor").andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/campanhas").with(token("sub-campo"))).andExpect(status().isForbidden());

		var corpo = """
				{"nome":"Mutirão da concessão","recorte":%s,"acao":"LIGACAO","prazo":"%s"}""".formatted(recorte,
				hoje.plusDays(30));
		var campanha = id(postar("/api/v1/campanhas", corpo, "sub-admin").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ATIVA"))
			.andExpect(jsonPath("$.ligadosNaCriacao").value(8761))
			.andExpect(jsonPath("$.funil.total").value(6))
			.andExpect(jsonPath("$.funil.semEquipe").value(6)));
		// Um imóvel tem no máximo uma OS aberta: o mesmo recorte não gera nada.
		postar("/api/v1/campanhas", corpo, "sub-admin").andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/campanhas/{id}", campanha).with(token("sub-gestor"))).andExpect(status().isNotFound());

		var ordens = lerOrdens(campanha, "");
		assertThat(ordens.get("total").asInt()).isEqualTo(6);
		assertThat(ordens.get("itens").get(0).get("matricula").asString()).isEqualTo("B-001");
		assertThat(ordens.get("itens").get(0).get("prioridade").asString()).isEqualTo("ALTA");
		assertThat(ordem(ordens, "2001001").get("servicoCodigo").asString()).isEqualTo("RELIGACAO");
		assertThat(ordem(ordens, "2001002").get("servicoCodigo").asString()).isEqualTo("LIGACAO_NOVA");
		assertThat(ordem(ordens, "2001002").get("origem").asString()).isEqualTo("CAMPANHA");
		assertThat(ordem(ordens, "2001002").get("status").asString()).isEqualTo("PLANEJADA");

		// Equipe própria na Cidade A; terceirizada exige a empresa.
		postar("/api/v1/equipes", """
				{"unidadeId":"%s","nome":"Empreiteira X","tipo":"TERCEIRIZADA","ativa":true,"membros":[]}"""
			.formatted(t.cidadeA()), "sub-admin").andExpect(status().isUnprocessableContent());
		var equipeJson = """
				{"unidadeId":"%s","nome":"Equipe Cidade A","tipo":"PROPRIA","ativa":%s,"membros":["%s"]}""";
		var equipe = id(postar("/api/v1/equipes", equipeJson.formatted(t.cidadeA(), true, campo), "sub-admin")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.membros[0].email").value("campo@campanhas.teste")));

		// Sem lista: as planejadas por prioridade; B-001 fica fora da área da equipe.
		var distribuicao = """
				{"equipeId":"%s","data":"%s","ordens":[],"quantidade":10}""";
		postar("/api/v1/campanhas/" + campanha + "/distribuir", distribuicao.formatted(equipe, hoje.minusDays(1)),
				"sub-admin")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/campanhas/" + campanha + "/distribuir", distribuicao.formatted(equipe, hoje), "sub-admin")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enviadas").value(5))
			.andExpect(jsonPath("$.foraDaArea").value(1));

		mvc.perform(get("/api/v1/minhas-ordens").with(token("sub-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(5))
			.andExpect(jsonPath("$[0].status").value("PROGRAMADA"))
			.andExpect(jsonPath("$[0].dataProgramada").value(hoje.toString()))
			.andExpect(jsonPath("$[0].matricula").value("2001001"));
		mvc.perform(get("/api/v1/minhas-ordens").with(token("sub-outro")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		var os2 = ordem(ordens, "2001002").get("id").asString();
		var os3 = ordem(ordens, "2001003").get("id").asString();
		var os4 = ordem(ordens, "2001004").get("id").asString();
		var os5 = ordem(ordens, "2001005").get("id").asString();
		resultado(os2, "{\"resultado\":\"EXECUTADA\"}", "sub-outro").andExpect(status().isNotFound());
		resultado(os2, "{\"resultado\":\"EXECUTADA\"}", "sub-campo").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENCERRADA"))
			.andExpect(jsonPath("$.resultado").value("EXECUTADA"))
			.andExpect(jsonPath("$.quantidadeExecutada").value(1))
			.andExpect(jsonPath("$.conferencia").value("PENDENTE"))
			.andExpect(jsonPath("$.resultadoPor").value("campo@campanhas.teste"));
		resultado(os2, "{\"resultado\":\"EXECUTADA\"}", "sub-campo").andExpect(status().isConflict());

		// Motivos do catálogo: não execução exige um do tipo certo.
		var semInteresse = motivo("NAO_EXECUCAO", "SEM_INTERESSE");
		var enderecoNaoEncontrado = motivo("DEVOLUCAO", "ENDERECO_NAO_ENCONTRADO");
		resultado(os3, "{\"resultado\":\"NAO_EXECUTADA\"}", "sub-campo").andExpect(status().isUnprocessableContent());
		resultado(os3, "{\"resultado\":\"NAO_EXECUTADA\",\"motivoId\":\"%s\"}".formatted(enderecoNaoEncontrado),
				"sub-campo")
			.andExpect(status().isUnprocessableContent());
		resultado(os3, "{\"resultado\":\"NAO_EXECUTADA\",\"motivoId\":\"%s\"}".formatted(semInteresse), "sub-campo")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.motivoNome").value("Morador sem interesse"))
			.andExpect(jsonPath("$.quantidadeExecutada").value(0))
			.andExpect(jsonPath("$.conferencia").value("NAO_SE_APLICA"));
		// A equipe devolve para a programação (D8).
		postar("/api/v1/minhas-ordens/" + os4 + "/devolver", "{\"motivoId\":\"%s\"}".formatted(semInteresse),
				"sub-campo")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/minhas-ordens/" + os4 + "/devolver",
				"{\"motivoId\":\"%s\",\"observacao\":\"Rua sem placa\"}".formatted(enderecoNaoEncontrado), "sub-campo")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DEVOLVIDA"))
			.andExpect(jsonPath("$.observacao").value("Rua sem placa"));
		resultado(os4, "{\"resultado\":\"EXECUTADA\"}", "sub-campo").andExpect(status().isConflict());
		// O gestor registra pela empreiteira sem acesso (motivos de não execução).
		mvc.perform(get("/api/v1/campanhas/motivos").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.codigo == 'SEM_INTERESSE')].tipo").value("NAO_EXECUCAO"))
			.andExpect(jsonPath("$[?(@.tipo == 'DEVOLUCAO')]").isEmpty());
		mvc.perform(put("/api/v1/campanhas/ordens/{id}/resultado", os5).contentType(MediaType.APPLICATION_JSON)
			.content("{\"resultado\":\"EXECUTADA\"}")
			.with(token("sub-admin"))).andExpect(status().isOk());

		// Equipe com OS em campo não é desativada.
		mvc.perform(put("/api/v1/equipes/{id}", equipe).contentType(MediaType.APPLICATION_JSON)
			.content(equipeJson.formatted(t.cidadeA(), false, campo))
			.with(token("sub-admin"))).andExpect(status().isConflict());

		// RN-19: a carga do mês mostra 2001002 ligado (confirmada). 2001005 foi executada
		// em julho e as cargas seguintes não mostram a ligação (divergente).
		try (var c = BancoDeTeste.comoSuperusuario(); var comando = c.createStatement()) {
			comando.execute("UPDATE ordem_servico SET resultado_em = TIMESTAMPTZ '2026-07-15 10:00-03' WHERE id = '"
					+ UUID.fromString(os5) + "'");
		}
		carregar("matricula;unidade;situacao\n2001002;%s;LIGADO\n".formatted(t.setorA1()));
		var mes = YearMonth.now(FUSO).toString();
		var depois = lerOrdens(campanha, "");
		assertThat(ordem(depois, "2001002").get("conferencia").asString()).isEqualTo("CONFIRMADA");
		assertThat(ordem(depois, "2001002").get("confirmadaEm").asString()).isEqualTo(mes);
		assertThat(ordem(depois, "2001005").get("conferencia").asString()).isEqualTo("DIVERGENTE");

		mvc.perform(get("/api/v1/campanhas/{id}", campanha).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.campanha.funil.executadas").value(2))
			.andExpect(jsonPath("$.campanha.funil.confirmadas").value(1))
			.andExpect(jsonPath("$.campanha.funil.divergentes").value(1))
			.andExpect(jsonPath("$.campanha.funil.naoExecutadas").value(1))
			.andExpect(jsonPath("$.campanha.funil.devolvidas").value(1))
			.andExpect(jsonPath("$.campanha.funil.semEquipe").value(2))
			.andExpect(jsonPath("$.equipes[0].executadas").value(2))
			.andExpect(jsonPath("$.equipes[0].devolvidas").value(1))
			.andExpect(jsonPath("$.motivos.length()").value(2))
			.andExpect(jsonPath("$.motivos[?(@.codigo == 'SEM_INTERESSE')].tipo").value("NAO_EXECUCAO"));
		mvc.perform(get("/api/v1/alertas").with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.tipo == 'ORDEM_DIVERGENTE')].link")
				.value("/campanhas/" + campanha + "?conferencia=DIVERGENTE"));

		// Encerrar cancela as que não começaram (B-001 planejada, 2001001 programada e
		// 2001004 devolvida); a equipe continua vendo as encerradas dos últimos 30 dias.
		postar("/api/v1/campanhas/" + campanha + "/encerrar", "", "sub-admin").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENCERRADA"))
			.andExpect(jsonPath("$.funil.canceladas").value(3));
		postar("/api/v1/campanhas/" + campanha + "/cancelar", "", "sub-admin").andExpect(status().isConflict());
		mvc.perform(get("/api/v1/minhas-ordens").with(token("sub-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[*].status")
				.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ENCERRADA"))));
	}

	private String motivo(String tipo, String codigo) throws Exception {
		var resposta = mvc.perform(get("/api/v1/minhas-ordens/motivos").with(token("sub-campo")))
			.andExpect(status().isOk())
			.andReturn();
		for (var m : json.readTree(resposta.getResponse().getContentAsString())) {
			if (m.get("tipo").asString().equals(tipo) && m.get("codigo").asString().equals(codigo)) {
				return m.get("id").asString();
			}
		}
		throw new AssertionError("Motivo não encontrado: " + tipo + "/" + codigo);
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(sub)));
	}

	private ResultActions resultado(String ordem, String corpo, String sub) throws Exception {
		return mvc.perform(put("/api/v1/minhas-ordens/{id}/resultado", ordem).contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.with(token(sub)));
	}

	private JsonNode lerOrdens(String campanha, String filtro) throws Exception {
		var resposta = mvc.perform(get("/api/v1/campanhas/" + campanha + "/ordens" + filtro).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(resposta.getResponse().getContentAsString());
	}

	private static JsonNode ordem(JsonNode pagina, String matricula) {
		for (var item : pagina.get("itens")) {
			if (item.get("matricula").asString().equals(matricula)) {
				return item;
			}
		}
		throw new AssertionError("OS não encontrada: " + matricula);
	}

	private String id(ResultActions resposta) throws Exception {
		return json.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asString();
	}

	private void carregar(String csv) throws Exception {
		var fonte = id(postar("/api/v1/integracoes/fontes", """
				{"nome":"Comercial campanhas","tipo":"AGENTE","atrasoMaximoHoras":48,"ativo":true}""", "sub-admin")
			.andExpect(status().isCreated()));
		mvc.perform(multipart("/api/v1/integracoes/fontes/{id}/cargas", fonte)
			.file(new MockMultipartFile("arquivo", "carga.csv", "text/csv", csv.getBytes()))
			.param("competencia", YearMonth.now(FUSO).toString())
			.with(token("sub-admin"))).andExpect(status().isOk());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
