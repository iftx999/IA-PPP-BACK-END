package dev.aurora.simulador.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.PdfDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.contratos.service.ExtratorContrato;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0035: penalidade aprovada vira regra calculável; o simulador projeta a adesão da
 * concessão e estima a multa no ritmo atual e no cenário.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SimuladorApiTest {

	private static final String MULTA_DE_UM_DECIMO = """
			{"formula":"PERCENTUAL_DA_BASE_POR_PONTO","valor":0.1,"baseValor":10000000,
			 "baseDescricao":"Faturamento mensal","contagem":"POR_PONTO_OU_FRACAO","periodicidade":"MENSAL"}""";

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	ExtratorContrato extrator;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Simulador");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@simulador.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "contrato:ler", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@simulador.teste", t.grupo(), leitura);
		var indicadores = DadosDeTeste.criarPerfil(t, "Só indicadores", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-indicadores", "indicadores@simulador.teste", t.grupo(), indicadores);
		DadosDeTeste.criarUsuario(t, "sub-cidade", "cidade@simulador.teste", t.cidadeA(), leitura);
	}

	@BeforeEach
	void semIa() {
		when(extrator.disponivel()).thenReturn(false);
	}

	@Test
	void penalidadeCalculavelEntraNaSimulacaoDaConcessao() throws Exception {
		var contrato = criarContrato();
		var documento = documento(contrato);
		var penalidade = criarItem(contrato, documento, "PENALIDADE");
		var obrigacao = criarItem(contrato, documento, "OBRIGACAO");

		// Só penalidade aprovada; só quem gerencia contratos; base obrigatória no
		// percentual.
		regra(penalidade, MULTA_DE_UM_DECIMO, "sub-admin").andExpect(status().isConflict());
		aprovar(penalidade);
		aprovar(obrigacao);
		regra(obrigacao, MULTA_DE_UM_DECIMO, "sub-admin").andExpect(status().isConflict());
		regra(penalidade, MULTA_DE_UM_DECIMO, "sub-leitura").andExpect(status().isForbidden());
		regra(penalidade, MULTA_DE_UM_DECIMO.replace("\"baseValor\":10000000,", ""), "sub-admin")
			.andExpect(status().isUnprocessableContent());
		regra(penalidade, MULTA_DE_UM_DECIMO.replace("0.1", "0"), "sub-admin")
			.andExpect(status().isUnprocessableContent());
		regra(penalidade, MULTA_DE_UM_DECIMO, "sub-admin").andExpect(status().isOk())
			.andExpect(jsonPath("$.unidadeId").value(t.concessao().toString()))
			.andExpect(jsonPath("$.baseDescricao").value("Faturamento mensal"))
			.andExpect(jsonPath("$.atualizadoPor").value("admin@simulador.teste"));
		mvc.perform(get("/api/v1/contratos/{id}/regras-de-penalidade", contrato).with(token("sub-leitura")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].itemId").value(penalidade.toString()));

		// Padrão do cenário: o ritmo necessário (90/mês) zera a multa; o atual (81/mês)
		// fica
		// 0,31 pp abaixo em dez/2026 → 1 ponto × 0,1% × R$ 10 mi.
		simular(t.concessao(), "", "sub-leitura").andExpect(status().isOk())
			.andExpect(jsonPath("$.competencia").value("2026-08"))
			.andExpect(jsonPath("$.ritmoAtual").value(81.0))
			.andExpect(jsonPath("$.meta.prazo").value("2026-12"))
			.andExpect(jsonPath("$.ligacoesNecessariasPorMes").value(90))
			.andExpect(jsonPath("$.fim").value("2027-12"))
			.andExpect(jsonPath("$.regrasVisiveis").value(true))
			.andExpect(jsonPath("$.regras[0].multaRitmoAtual").value(10000.00))
			.andExpect(jsonPath("$.regras[0].multaCenario").value(0))
			.andExpect(jsonPath("$.ritmoAtualProjetado.mesAtingimento").value("2027-01"))
			.andExpect(jsonPath("$.ritmoAtualProjetado.multaTotal").value(10000.00))
			.andExpect(jsonPath("$.cenario.ligacoesPorMes").value(90))
			.andExpect(jsonPath("$.cenario.mesAtingimento").value("2026-12"))
			.andExpect(jsonPath("$.cenario.multaTotal").value(0))
			.andExpect(jsonPath("$.cenario.meses.length()").value(17));

		// Sem unidade, abre onde há penalidade calculável e lista os atalhos.
		mvc.perform(get("/api/v1/simulador").with(token("sub-leitura")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unidadeId").value(t.concessao().toString()))
			.andExpect(jsonPath("$.unidadesComRegra[0].id").value(t.concessao().toString()))
			.andExpect(jsonPath("$.unidadesComRegra[0].regras").value(1));

		// Cenário pedido sem novas ligações, 3 meses após o prazo: 4 pontos × R$ 10 mil ×
		// 4.
		simular(t.concessao(), "?ligacoesPorMes=0&mesesAposPrazo=3", "sub-leitura")
			.andExpect(jsonPath("$.cenario.multaTotal").value(160000.00))
			.andExpect(jsonPath("$.cenario.mesAtingimento").value(nullValue()));

		// Sem contrato:ler a projeção sai, sem regras nem multa.
		simular(t.concessao(), "", "sub-indicadores").andExpect(status().isOk())
			.andExpect(jsonPath("$.regrasVisiveis").value(false))
			.andExpect(jsonPath("$.regras.length()").value(0))
			.andExpect(jsonPath("$.unidadesComRegra.length()").value(0))
			.andExpect(jsonPath("$.ritmoAtualProjetado.multaTotal").value(0));
		// Sem regras visíveis nem unidade pedida: a mais alta do escopo com medição, como
		// no painel.
		mvc.perform(get("/api/v1/simulador").with(token("sub-indicadores")))
			.andExpect(jsonPath("$.unidadeId").value(t.concessao().toString()));

		// A multa é aferida na concessão: a Cidade A é simulada contra a própria meta,
		// sem multa.
		simular(t.cidadeA(), "", "sub-cidade").andExpect(status().isOk())
			.andExpect(jsonPath("$.meta.valor").value(85))
			.andExpect(jsonPath("$.meta.prazo").value(nullValue()))
			.andExpect(jsonPath("$.regras.length()").value(0));
		// Fora do escopo é "não encontrada".
		simular(t.concessao(), "", "sub-cidade").andExpect(status().isNotFound());

		// Remover tira a regra da simulação; definir e remover vão para a trilha.
		mvc.perform(delete("/api/v1/contratos/itens/{id}/regra-de-penalidade", penalidade).with(token("sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/contratos/itens/{id}/regra-de-penalidade", penalidade).with(token("sub-admin")))
			.andExpect(status().isNotFound());
		simular(t.concessao(), "", "sub-admin").andExpect(jsonPath("$.regras.length()").value(0));
		assertThat(eventos("REGRA_DE_PENALIDADE_DEFINIDA", penalidade)).isEqualTo(1);
		assertThat(eventos("REGRA_DE_PENALIDADE_REMOVIDA", penalidade)).isEqualTo(1);
	}

	@Test
	void parametrosForaDosLimitesSaoRecusados() throws Exception {
		simular(t.concessao(), "?ligacoesPorMes=-1", "sub-admin").andExpect(status().isUnprocessableContent());
		simular(t.concessao(), "?mesesAposPrazo=61", "sub-admin").andExpect(status().isUnprocessableContent());
		simular(UUID.randomUUID(), "", "sub-admin").andExpect(status().isNotFound());
	}

	private ResultActions simular(UUID unidade, String parametros, String sub) throws Exception {
		var url = "/api/v1/simulador?unidade=" + unidade + (parametros.isEmpty() ? "" : "&" + parametros.substring(1));
		return mvc.perform(get(url).with(token(sub)));
	}

	private ResultActions regra(UUID item, String corpo, String sub) throws Exception {
		return mvc.perform(
				put("/api/v1/contratos/itens/{id}/regra-de-penalidade", item).contentType(MediaType.APPLICATION_JSON)
					.content(corpo)
					.with(token(sub)));
	}

	private void aprovar(UUID item) throws Exception {
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", item).with(token("sub-admin")))
			.andExpect(status().isNoContent());
	}

	private UUID criarContrato() throws Exception {
		var resposta = mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"numero":"S-%s","unidadeId":"%s","objeto":"Esgotamento sanitário","dataAssinatura":"2025-03-15",
					 "vigenciaFim":"2045-03-14"}""".formatted(UUID.randomUUID().toString().substring(0, 8),
					t.concessao()))
			.with(token("sub-admin"))).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID documento(UUID contrato) throws Exception {
		var pdf = PdfDeTeste.comPaginas(List.of("39.5 Multa por adesao abaixo da meta. " + UUID.randomUUID()));
		var resposta = mvc
			.perform(multipart("/api/v1/contratos/{id}/documentos", contrato)
				.file(new MockMultipartFile("arquivo", "original.pdf", "application/pdf", pdf))
				.param("tipo", "ORIGINAL")
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		var id = UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
		for (int tentativa = 0; tentativa < 100; tentativa++) {
			var situacao = json
				.readTree(mvc.perform(get("/api/v1/contratos/documentos/{id}", id).with(token("sub-admin")))
					.andReturn()
					.getResponse()
					.getContentAsString())
				.get("situacao")
				.asString();
			if (situacao.equals("PRONTO")) {
				return id;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("documento não ficou pronto");
	}

	private UUID criarItem(UUID contrato, UUID documento, String tipo) throws Exception {
		var resposta = mvc
			.perform(post("/api/v1/contratos/{id}/itens", contrato).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"documentoId":"%s","item":{"tipo":"%s","titulo":"Multa por adesão abaixo da meta",
						 "resumo":"0,1%% do faturamento por ponto","clausula":"39.5","pagina":1,
						 "trecho":"Multa por adesao abaixo da meta","valorReferencia":0.1,"unidadeMedida":"%%"}}"""
					.formatted(documento, tipo))
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private static long eventos(String tipo, UUID item) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT count(*) FROM evento_auditoria "
						+ "WHERE tenant_id = ?::uuid AND tipo = ? AND recurso = ?")) {
			consulta.setString(1, t.id().toString());
			consulta.setString(2, tipo);
			consulta.setString(3, "item-contratual/" + item);
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getLong(1);
		}
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
