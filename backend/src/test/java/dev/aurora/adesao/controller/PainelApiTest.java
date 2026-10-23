package dev.aurora.adesao.controller;

import java.nio.charset.StandardCharsets;

import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.DadosDeTeste;
import dev.aurora.TestcontainersConfiguration;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Painel de ponta a ponta com os números do demo (docs/03): Concessão A em ago/2026. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PainelApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Painel");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-gestor", "gestor@painel.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade-a", "cidade@painel.teste", t.cidadeA());
	}

	@Test
	void painelDaConcessao() throws Exception {
		mvc.perform(get("/api/v1/painel").param("unidade", t.concessao().toString())
			.param("competencia", "2026-08")
			.with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.competencia", is("2026-08")))
			.andExpect(jsonPath("$.adesao.percentual", closeTo(76.8509, 0.0001)))
			.andExpect(jsonPath("$.adesao.semLigacao", is(2639)))
			.andExpect(jsonPath("$.variacaoPp", closeTo(0.7105, 0.0001)))
			.andExpect(jsonPath("$.situacao", is("ATENCAO")))
			.andExpect(jsonPath("$.meta.origem", is("Contrato 001/2025, cláusula 7.2, p. 2")))
			.andExpect(jsonPath("$.meta.seguidaDe", nullValue()))
			.andExpect(jsonPath("$.projecao.faltam", is(359)))
			.andExpect(jsonPath("$.serie", hasSize(2)))
			.andExpect(jsonPath("$.unidades", hasSize(2)))
			.andExpect(jsonPath("$.unidades[?(@.unidade.id == '%s')].situacao", t.cidadeB().toString())
				.value("ABAIXO_DO_ALERTA"))
			.andExpect(jsonPath("$.unidades[?(@.unidade.id == '%s')].situacao", t.cidadeA().toString())
				.value("META_ATINGIDA"));
	}

	@Test
	void setorSemMetaPropriaSegueAUnidadeAcima() throws Exception {
		mvc.perform(get("/api/v1/painel").param("unidade", t.setorA1().toString()).with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.acima.id", is(t.cidadeA().toString())))
			.andExpect(jsonPath("$.meta.seguidaDe.id", is(t.cidadeA().toString())))
			.andExpect(jsonPath("$.meta.valor", closeTo(85.0, 0.001)))
			.andExpect(jsonPath("$.situacao", is("META_ATINGIDA")));
	}

	@Test
	void unidadePadraoEhAMaisAltaDoEscopo() throws Exception {
		mvc.perform(get("/api/v1/painel").with(token("sub-gestor")))
			.andExpect(jsonPath("$.unidade.id", is(t.concessao().toString())));
		mvc.perform(get("/api/v1/painel").with(token("sub-cidade-a")))
			.andExpect(jsonPath("$.unidade.id", is(t.cidadeA().toString())))
			// a concessão (pai) está fora do escopo: não aparece
			.andExpect(jsonPath("$.acima", nullValue()));
	}

	@Test
	void unidadeForaDoEscopoRetorna404() throws Exception {
		mvc.perform(get("/api/v1/painel").param("unidade", t.concessao().toString()).with(token("sub-cidade-a")))
			.andExpect(status().isNotFound());
	}

	@Test
	void competenciaSemMedicaoRetorna404() throws Exception {
		mvc.perform(get("/api/v1/painel").param("competencia", "2020-01").with(token("sub-gestor")))
			.andExpect(status().isNotFound());
	}

	@Test
	void exportaOPainelEmCsvEPdfComAuditoria() throws Exception {
		var csv = mvc
			.perform(get("/api/v1/painel/exportacao.csv").param("unidade", t.concessao().toString())
				.param("competencia", "2026-08")
				.with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"painel-")))
			.andReturn();
		var linhas = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).lines().toList();
		assertThat(linhas.get(0)).startsWith("\uFEFFunidade;tipo;competencia;adesao_pct;ligados;com_rede");
		// Concessão A: 8.761 de 11.400 = 76,85%, meta 80 (do contrato), atenção.
		assertThat(linhas.get(1)).contains(";2026-08;76,85;8761;11400;2639;").contains("Contrato 001/2025");
		assertThat(linhas).hasSize(4);

		var pdf = mvc
			.perform(get("/api/v1/painel/exportacao.pdf").param("unidade", t.concessao().toString())
				.with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andReturn();
		try (var lido = Loader.loadPDF(pdf.getResponse().getContentAsByteArray())) {
			var texto = new PDFTextStripper().getText(lido);
			assertThat(texto).contains("Painel de adesão").contains("76,85%").contains("Unidades abaixo");
		}
		mvc.perform(get("/api/v1/painel/exportacao.xls").with(token("sub-gestor"))).andExpect(status().isNotFound());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
