package dev.aurora.contratos.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.PdfDeTeste;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.comum.Antivirus;
import org.junit.jupiter.api.BeforeAll;
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
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Antivírus no envio de contratos (doc 09, 9.7; ADR 0033), com antivírus obrigatório. */
@SpringBootTest(properties = "aurora.antivirus.obrigatorio=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AntivirusNoEnvioApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	Antivirus antivirus;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Antivírus");
		DadosDeTeste.criarUsuario(t, "sub-admin", "admin@av.teste", t.grupo());
	}

	@Test
	void arquivoInfectadoERecusadoNaoEGravadoEFicaNaTrilha() throws Exception {
		when(antivirus.verificar(any())).thenReturn(new Antivirus.Infectado("Pdf.Exploit.Teste"));
		var contrato = criarContrato();

		mvc.perform(multipart("/api/v1/contratos/{id}/documentos", contrato).file(pdf()).with(token()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", containsString("recusado pelo antivírus (Pdf.Exploit.Teste)")));

		assertThat(contar("SELECT count(*) FROM documento_contrato WHERE contrato_id = '%s'".formatted(contrato)))
			.isZero();
		assertThat(contar(("SELECT count(*) FROM evento_auditoria WHERE tenant_id = '%s'"
				+ " AND tipo = 'ARQUIVO_RECUSADO_PELO_ANTIVIRUS' AND detalhes ->> 'ameaca' = 'Pdf.Exploit.Teste'")
			.formatted(t.id()))).isEqualTo(1);
	}

	@Test
	void antivirusObrigatorioForaDoArRecusaOEnvio() throws Exception {
		when(antivirus.verificar(any())).thenReturn(new Antivirus.NaoVerificado("fora do ar"));
		var contrato = criarContrato();

		mvc.perform(multipart("/api/v1/contratos/{id}/documentos", contrato).file(pdf()).with(token()))
			.andExpect(status().isServiceUnavailable());
		assertThat(contar("SELECT count(*) FROM documento_contrato WHERE contrato_id = '%s'".formatted(contrato)))
			.isZero();
	}

	@Test
	void arquivoLimpoSegueNormalmente() throws Exception {
		when(antivirus.verificar(any())).thenReturn(new Antivirus.Limpo());
		var contrato = criarContrato();

		mvc.perform(multipart("/api/v1/contratos/{id}/documentos", contrato).file(pdf()).with(token()))
			.andExpect(status().isCreated());
	}

	private UUID criarContrato() throws Exception {
		var resposta = mvc.perform(post("/api/v1/contratos").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"numero":"AV-%s","unidadeId":"%s","objeto":"Esgotamento sanitário","dataAssinatura":"2025-03-15",
					 "vigenciaFim":"2055-03-14"}""".formatted(UUID.randomUUID().toString().substring(0, 8),
					t.concessao()))
			.with(token())).andExpect(status().isCreated()).andReturn();
		return UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
	}

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf",
				PdfDeTeste.comPaginas(List.of("Contrato de teste " + UUID.randomUUID())));
	}

	private static RequestPostProcessor token() {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject("sub-admin"));
	}

	private static long contar(String sql) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario();
				var consulta = c.createStatement();
				var r = consulta.executeQuery(sql)) {
			r.next();
			return r.getLong(1);
		}
	}

}
