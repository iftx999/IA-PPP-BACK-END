package dev.aurora.contratos.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.PdfDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.contratos.model.Proposta;
import dev.aurora.contratos.model.ResultadoExtracao;
import dev.aurora.contratos.service.ExtratorContrato;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContratosApiTest {

	private static final String PAGINA_METAS = """
			7. DAS METAS
			7.2 A adesao minima ao sistema de esgoto no Setor A1
			sera de 85% ate 31/12/2030, com alerta em 80%.""";

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	ExtratorContrato extrator;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Contratos");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@contratos.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura de contratos", "contrato:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@contratos.teste", t.grupo(), leitura);
		DadosDeTeste.criarUsuario(t, "sub-sem", "sem@contratos.teste", t.grupo(),
				DadosDeTeste.criarPerfil(t, "Sem contratos", "unidade:ler"));
	}

	@BeforeEach
	void extratorPadrao() throws Exception {
		when(extrator.disponivel()).thenReturn(true);
		when(extrator.extrair(any(), any())).thenReturn(new ResultadoExtracao(List.of(
				// Válido: trecho literal da página 2, unidade pelo nome.
				new Proposta("META", "Adesão mínima no Setor A1", "85% até 2030", "7.2", 2,
						"A adesao minima ao sistema de esgoto no Setor A1 sera de 85%", new BigDecimal("85"), "%",
						LocalDate.of(2030, 12, 31), null, null, new BigDecimal("80"), null, new BigDecimal("0.92")),
				// Trecho inventado: descartado.
				new Proposta("PRAZO", "Prazo inventado", "x", "9.9", 2, "Este texto não existe no contrato", null, null,
						null, null, null, null, null, new BigDecimal("0.4")),
				// Sem cláusula: fica, mas não pode ser aprovado sem completar.
				new Proposta("OBRIGACAO", "Manter cadastro", "Cadastro atualizado", "", 1,
						"Contrato de teste da concessao", null, null, null, null, null, null, null, null)),
				"claude-opus-5-5", "extracao-itens-v1", List.of()));
	}

	@Test
	void fluxoCompletoDoEnvioAMetaAprovada() throws Exception {
		var contrato = criarContrato("C-" + UUID.randomUUID().toString().substring(0, 8));
		var pdf = PdfDeTeste.comPaginas(List.of("Contrato de teste da concessao\nPartes e objeto.", PAGINA_METAS));

		var documento = enviar(contrato, pdf);
		var pronto = aguardar(documento);
		assertThat(pronto.get("situacao").asString()).isEqualTo("PRONTO");
		assertThat(pronto.get("paginas").asInt()).isEqualTo(2);
		assertThat(pronto.get("itensPropostos").asInt()).isEqualTo(2);
		assertThat(pronto.get("itensDescartados").asInt()).isEqualTo(1);

		// Mesmo arquivo de novo: recusado.
		mvc.perform(
				multipart("/api/v1/contratos/{id}/documentos", contrato).file(arquivo(pdf)).with(token("sub-admin")))
			.andExpect(status().isConflict());

		var itens = itens(contrato);
		var meta = porTitulo(itens, "Adesão mínima no Setor A1");
		var semReferencia = porTitulo(itens, "Manter cadastro");
		assertThat(semReferencia.get("semReferencia").asBoolean()).isTrue();
		assertThat(meta.get("propostaIa").get("titulo").asString()).isEqualTo("Adesão mínima no Setor A1");
		// A IA não identificou a unidade: fica sem unidade até a revisão.
		assertThat(meta.get("unidadeId").isNull()).isTrue();

		// Aprovar sem os dados da meta é recusado; editar completa e preserva a proposta
		// da IA.
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", id(meta)).with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", is("Para aprovar uma meta, informe: unidade, início da vigência.")));
		mvc.perform(put("/api/v1/contratos/itens/{id}", id(meta)).contentType(MediaType.APPLICATION_JSON)
			.content(
					"""
							{"tipo":"META","titulo":"Adesão mínima no Setor A1","resumo":"85%% até 2030","clausula":"7.2","pagina":2,
							 "trecho":"A adesao minima ao sistema de esgoto no Setor A1 sera de 85%%","valorReferencia":85,
							 "valorAlerta":80,"unidadeId":"%s","vigenciaInicio":"2027-01-01","dataLimite":"2030-12-31"}"""
						.formatted(t.setorA1()))
			.with(token("sub-admin"))).andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", id(meta)).with(token("sub-admin")))
			.andExpect(status().isNoContent());

		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c
					.prepareStatement("SELECT origem, valor_meta FROM meta WHERE item_contratual_id = ?::uuid")) {
			consulta.setString(1, id(meta).toString());
			try (var r = consulta.executeQuery()) {
				assertThat(r.next()).isTrue();
				assertThat(r.getString(1)).startsWith("Contrato C-").endsWith(", cláusula 7.2, p. 2");
				assertThat(r.getBigDecimal(2)).isEqualByComparingTo("85");
			}
		}

		// Sem cláusula não aprova; rejeitar exige motivo; item revisado não muda mais.
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", id(semReferencia)).with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(
				post("/api/v1/contratos/itens/{id}/rejeicao", id(semReferencia)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"motivo\":\"Já previsto no anexo\"}")
					.with(token("sub-admin")))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/contratos/itens/{id}/aprovacao", id(semReferencia)).with(token("sub-admin")))
			.andExpect(status().isConflict());

		mvc.perform(get("/api/v1/contratos/{id}", contrato).with(token("sub-leitura")))
			.andExpect(jsonPath("$.contrato.itensAprovados", is(1)))
			.andExpect(jsonPath("$.contrato.itensPendentes", is(0)));
		mvc.perform(get("/api/v1/contratos/documentos/{id}/arquivo", documento).with(token("sub-leitura")))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF));
	}

	@Test
	void pdfInvalidoDigitalizadoEIaDesligada() throws Exception {
		var contrato = criarContrato("C-" + UUID.randomUUID().toString().substring(0, 8));
		mvc.perform(multipart("/api/v1/contratos/{id}/documentos", contrato)
			.file(new MockMultipartFile("arquivo", "x.pdf", "application/pdf", "não é pdf".getBytes()))
			.with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", is("O arquivo não é um PDF.")));
		mvc.perform(multipart("/api/v1/contratos/{id}/documentos", contrato)
			.file(new MockMultipartFile("arquivo", "x.pdf", "application/pdf", "%PDF-1.7 quebrado".getBytes()))
			.with(token("sub-admin"))).andExpect(status().isUnprocessableContent());

		var digitalizado = aguardar(enviar(contrato, PdfDeTeste.comPaginas(List.of("", ""))));
		assertThat(digitalizado.get("situacao").asString()).isEqualTo("SEM_TEXTO");

		when(extrator.disponivel()).thenReturn(false);
		var documento = enviar(contrato, PdfDeTeste.comPaginas(List.of(PAGINA_METAS)));
		var semIa = aguardar(documento);
		assertThat(semIa.get("situacao").asString()).isEqualTo("PRONTO");
		assertThat(semIa.get("mensagem").asString()).contains("cadastre os itens manualmente");

		// Item manual na página citada.
		mvc.perform(post("/api/v1/contratos/{id}/itens", contrato).contentType(MediaType.APPLICATION_JSON).content("""
				{"documentoId":"%s","item":{"tipo":"PRAZO","titulo":"Meta 2030","resumo":"Prazo","clausula":"7.2",
				 "pagina":1,"trecho":"sera de 85%% ate 31/12/2030"}}""".formatted(documento)).with(token("sub-admin")))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/contratos/{id}/itens", contrato).contentType(MediaType.APPLICATION_JSON).content("""
				{"documentoId":"%s","item":{"tipo":"PRAZO","titulo":"x","resumo":"x","clausula":"1","pagina":5,
				 "trecho":"x"}}""".formatted(documento)).with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).param("status", "PROPOSTO").with(token("sub-admin")))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].origem", is("MANUAL")));
	}

	@Test
	void permissoes() throws Exception {
		mvc.perform(get("/api/v1/contratos").with(token("sub-sem"))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON).content("""
				{"numero":"X","unidadeId":"%s"}""".formatted(t.concessao())).with(token("sub-leitura")))
			.andExpect(status().isForbidden());
		var numero = "C-" + UUID.randomUUID().toString().substring(0, 8);
		criarContrato(numero);
		mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON).content("""
				{"numero":"%s","unidadeId":"%s"}""".formatted(numero, t.concessao())).with(token("sub-admin")))
			.andExpect(status().isConflict());
		var outro = DadosDeTeste.criarTenant("Outro");
		mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON).content("""
				{"numero":"Y","unidadeId":"%s"}""".formatted(outro.concessao())).with(token("sub-admin")))
			.andExpect(status().isUnprocessableContent());
	}

	private UUID criarContrato(String numero) throws Exception {
		var resposta = mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON).content("""
				{"numero":"%s","unidadeId":"%s","objeto":"Esgotamento sanitário","dataAssinatura":"2025-03-15",
				 "vigenciaFim":"2055-03-14"}""".formatted(numero, t.concessao())).with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private UUID enviar(UUID contrato, byte[] pdf) throws Exception {
		var resposta = mvc
			.perform(multipart("/api/v1/contratos/{id}/documentos", contrato).file(arquivo(pdf))
				.with(token("sub-admin")))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	/** O processamento é assíncrono: consulta até sair das etapas intermediárias. */
	private JsonNode aguardar(UUID documento) throws Exception {
		for (int tentativa = 0; tentativa < 100; tentativa++) {
			var corpo = json
				.readTree(mvc.perform(get("/api/v1/contratos/documentos/{id}", documento).with(token("sub-admin")))
					.andReturn()
					.getResponse()
					.getContentAsString());
			var situacao = corpo.get("situacao").asString();
			if (!List.of("RECEBIDO", "RECONHECENDO_TEXTO", "PROCESSANDO").contains(situacao)) {
				return corpo;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("documento não terminou de processar");
	}

	private JsonNode itens(UUID contrato) throws Exception {
		return json.readTree(mvc.perform(get("/api/v1/contratos/{id}/itens", contrato).with(token("sub-admin")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private static JsonNode porTitulo(JsonNode itens, String titulo) {
		for (var item : itens) {
			if (item.get("titulo").asString().equals(titulo)) {
				return item;
			}
		}
		throw new AssertionError("item não encontrado: " + titulo);
	}

	private static UUID id(JsonNode item) {
		return UUID.fromString(item.get("id").asString());
	}

	private static MockMultipartFile arquivo(byte[] pdf) {
		return new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", pdf);
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
