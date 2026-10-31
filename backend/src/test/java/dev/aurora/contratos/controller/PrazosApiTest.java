package dev.aurora.contratos.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.PdfDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.alertas.service.AvaliadorAlertas;
import dev.aurora.contexto.ContextoAcesso;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
 * ADR 0024: prazo contratual aprovado gera alerta até ser marcado como cumprido; o fim da
 * vigência do contrato também é acompanhado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrazosApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	AvaliadorAlertas avaliador;

	@Autowired
	ContextosDoSistema contextos;

	@MockitoBean
	ExtratorContrato extrator;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Prazos");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@prazos.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura de contratos", "contrato:ler", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@prazos.teste", t.grupo(), leitura);
	}

	@BeforeEach
	void semIa() {
		when(extrator.disponivel()).thenReturn(false);
	}

	@Test
	void prazoEmAlertaAteSerCumpridoEFimDoContrato() throws Exception {
		var hoje = LocalDate.now();
		var contrato = criarContrato(hoje.plusDays(100));
		var documento = documento(contrato);
		var prazo = criarItem(contrato, documento, "PRAZO", hoje.plusDays(10));
		var obrigacao = criarItem(contrato, documento, "OBRIGACAO", null);

		// Proposto ainda não conta.
		avaliar();
		assertThat(chavesAbertas()).doesNotContain("PRAZO:" + prazo);
		aprovar(prazo);
		aprovar(obrigacao);
		avaliar();
		assertThat(chavesAbertas()).contains("PRAZO:" + prazo, "CONTRATO:" + contrato);
		mvc.perform(get("/api/v1/alertas").with(token("sub-admin")))
			.andExpect(jsonPath("$[?(@.tipo == 'PRAZO_CONTRATUAL')].severidade", hasItem("CRITICO")))
			.andExpect(jsonPath("$[?(@.tipo == 'PRAZO_CONTRATUAL')].titulo",
					hasItem("Vigência do contrato %s termina em breve".formatted(numero(contrato)))));

		// Só quem gerencia contratos marca; só prazos aprovados; não repete.
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", prazo).with(token("sub-leitura")))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", obrigacao).with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", prazo).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.proximoVencimento").value(nullValue()))
			.andExpect(jsonPath("$.cumprimentos[0].vencimento").value(hoje.plusDays(10).toString()));
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", prazo).with(token("sub-admin")))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).with(token("sub-leitura")))
			.andExpect(jsonPath("$[?(@.id == '%s')].agenda.cumprimentos[0].cumpridoPor".formatted(prazo),
					hasItem("admin@prazos.teste")));

		assertThat(avaliar().resolvidos()).isEqualTo(1);
		assertThat(chavesAbertas()).doesNotContain("PRAZO:" + prazo).contains("CONTRATO:" + contrato);

		// Desfazer reabre o acompanhamento.
		mvc.perform(delete("/api/v1/contratos/itens/{id}/cumprimento", prazo).with(token("sub-admin")))
			.andExpect(status().isOk());
		avaliar();
		assertThat(chavesAbertas()).contains("PRAZO:" + prazo);
		mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).with(token("sub-admin")))
			.andExpect(jsonPath("$[?(@.id == '%s')].agenda.proximoVencimento".formatted(prazo),
					hasItem(hoje.plusDays(10).toString())));
		assertThat(eventos("PRAZO_CONTRATUAL_CUMPRIDO", prazo)).isEqualTo(1);
		assertThat(eventos("PRAZO_CONTRATUAL_REABERTO", prazo)).isEqualTo(1);
	}

	@Test
	void obrigacaoMensalSemDataGanhaAgendaEAvancaACadaCumprimento() throws Exception {
		var hoje = LocalDate.now();
		var contrato = criarContrato(hoje.plusYears(2));
		var documento = documento(contrato);
		var obrigacao = criarItem(contrato, documento, "OBRIGACAO", null, "mensalmente");
		var penalidade = criarItem(contrato, documento, "PENALIDADE", null, "mensal");
		aprovar(obrigacao);
		aprovar(penalidade);

		// Sem data não há o que acompanhar; a repetição é sugerida pelo texto do
		// contrato.
		mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).with(token("sub-admin")))
			.andExpect(jsonPath("$[?(@.id == '%s')].recorrenciaSugerida".formatted(obrigacao), hasItem("MENSAL")))
			.andExpect(jsonPath("$[?(@.id == '%s')].agenda".formatted(obrigacao), hasItem(nullValue())))
			.andExpect(jsonPath("$[?(@.id == '%s')].recorrenciaSugerida".formatted(penalidade), hasItem(nullValue())));
		agenda(obrigacao, null, "MENSAL").andExpect(status().isUnprocessableContent());
		agenda(penalidade, hoje.toString(), "MENSAL").andExpect(status().isUnprocessableContent());
		agenda(obrigacao, hoje.plusYears(3).toString(), "MENSAL").andExpect(status().isUnprocessableContent());
		mvc.perform(put("/api/v1/contratos/itens/{id}/agenda", obrigacao).contentType(MediaType.APPLICATION_JSON)
			.content("{\"primeiroVencimento\":\"%s\",\"recorrencia\":\"MENSAL\"}".formatted(hoje.plusDays(5)))
			.with(token("sub-leitura"))).andExpect(status().isForbidden());

		var primeiro = hoje.plusDays(5);
		agenda(obrigacao, primeiro.toString(), "MENSAL").andExpect(status().isOk())
			.andExpect(jsonPath("$.recorrencia").value("MENSAL"))
			.andExpect(jsonPath("$.proximoVencimento").value(primeiro.toString()));
		avaliar();
		assertThat(chavesAbertas()).contains("PRAZO:" + obrigacao + ":" + primeiro);

		// Cumprir passa ao mês seguinte: o alerta deste vencimento se encerra.
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", obrigacao).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.proximoVencimento").value(primeiro.plusMonths(1).toString()));
		mvc.perform(post("/api/v1/contratos/itens/{id}/cumprimento", obrigacao).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.proximoVencimento").value(primeiro.plusMonths(2).toString()))
			.andExpect(jsonPath("$.cumprimentos.length()").value(2))
			.andExpect(jsonPath("$.cumprimentos[0].vencimento").value(primeiro.plusMonths(1).toString()));
		avaliar();
		assertThat(chavesAbertas()).noneMatch(c -> c.startsWith("PRAZO:" + obrigacao));

		// Desfazer volta só o último.
		mvc.perform(delete("/api/v1/contratos/itens/{id}/cumprimento", obrigacao).with(token("sub-admin")))
			.andExpect(jsonPath("$.proximoVencimento").value(primeiro.plusMonths(1).toString()));
		assertThat(eventos("AGENDA_DE_PRAZO_DEFINIDA", obrigacao)).isEqualTo(1);
		assertThat(eventos("PRAZO_CONTRATUAL_CUMPRIDO", obrigacao)).isEqualTo(2);
		assertThat(eventos("PRAZO_CONTRATUAL_REABERTO", obrigacao)).isEqualTo(1);
	}

	private org.springframework.test.web.servlet.ResultActions agenda(UUID item, String primeiro, String recorrencia)
			throws Exception {
		return mvc.perform(put("/api/v1/contratos/itens/{id}/agenda", item).contentType(MediaType.APPLICATION_JSON)
			.content("{\"primeiroVencimento\":%s,\"recorrencia\":\"%s\"}"
				.formatted(primeiro == null ? "null" : "\"" + primeiro + "\"", recorrencia))
			.with(token("sub-admin")));
	}

	private AvaliadorAlertas.Resultado avaliar() throws Exception {
		return ContextoAcesso.executar(contextos.doTenant(t.id()).orElseThrow(), avaliador::avaliarIndicadores);
	}

	private void aprovar(UUID item) throws Exception {
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", item).with(token("sub-admin")))
			.andExpect(status().isNoContent());
	}

	private UUID criarContrato(LocalDate fim) throws Exception {
		var resposta = mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"numero":"P-%s","unidadeId":"%s","objeto":"Esgotamento sanitário","dataAssinatura":"2025-03-15",
					 "vigenciaFim":"%s"}""".formatted(UUID.randomUUID().toString().substring(0, 8), t.concessao(), fim))
			.with(token("sub-admin"))).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID documento(UUID contrato) throws Exception {
		var pdf = PdfDeTeste.comPaginas(List.of("9.1 A concessionaria entregara o cadastro. " + UUID.randomUUID()));
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

	private UUID criarItem(UUID contrato, UUID documento, String tipo, LocalDate dataLimite) throws Exception {
		return criarItem(contrato, documento, tipo, dataLimite, null);
	}

	private UUID criarItem(UUID contrato, UUID documento, String tipo, LocalDate dataLimite, String periodicidade)
			throws Exception {
		var resposta = mvc
			.perform(post("/api/v1/contratos/{id}/itens", contrato).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"documentoId":"%s","item":{"tipo":"%s","titulo":"Entrega do cadastro","resumo":"Prazo",
						 "clausula":"9.1","pagina":1,"trecho":"entregara o cadastro","dataLimite":%s,
						 "periodicidade":%s}}""".formatted(documento, tipo,
						dataLimite == null ? "null" : "\"" + dataLimite + "\"",
						periodicidade == null ? "null" : "\"" + periodicidade + "\""))
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private static List<String> chavesAbertas() throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c
					.prepareStatement("SELECT chave FROM alerta WHERE tenant_id = ?::uuid AND resolvido_em IS NULL")) {
			consulta.setString(1, t.id().toString());
			var linhas = consulta.executeQuery();
			var chaves = new java.util.ArrayList<String>();
			while (linhas.next()) {
				chaves.add(linhas.getString(1));
			}
			return chaves;
		}
	}

	private static String numero(UUID contrato) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.prepareStatement("SELECT numero FROM contrato WHERE id = ?::uuid")) {
			consulta.setString(1, contrato.toString());
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getString(1);
		}
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
