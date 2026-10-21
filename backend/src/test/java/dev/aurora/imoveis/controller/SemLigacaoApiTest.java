package dev.aurora.imoveis.controller;

import java.nio.charset.StandardCharsets;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SemLigacaoApiTest {

	private static Tenant t;

	private static Tenant outro;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Sem ligação");
		outro = DadosDeTeste.criarTenant("Outro");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.criarUsuario(t, "sub-comercial", "comercial@sl.teste", t.grupo());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@sl.teste", t.grupo(), leitura);
	}

	@Test
	void listaComTotaisEHistorico() throws Exception {
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.param("competencia", "2026-08")
			.with(token("sub-comercial")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totais.todos", is(5)))
			.andExpect(jsonPath("$.totais.factiveis", is(4)))
			.andExpect(jsonPath("$.totais.cortados", is(1)))
			.andExpect(jsonPath("$.matriculasVisiveis", is(true)))
			.andExpect(jsonPath("$.itens[0].matricula", is("2001001")))
			.andExpect(jsonPath("$.itens[0].situacao", is("CORTADO")))
			.andExpect(jsonPath("$.itens[0].ultimoMesLigado", is("2026-07")))
			.andExpect(jsonPath("$.itens[0].mesesSemLigacao", is(1)))
			.andExpect(jsonPath("$.itens[1].ultimoMesLigado", nullValue()))
			.andExpect(jsonPath("$.itens[1].primeiraCompetencia", is("2026-06")));
	}

	@Test
	void semPermissaoDeDadosPessoaisAsMatriculasVemMascaradas() throws Exception {
		mvc.perform(
				get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString()).with(token("sub-leitura")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matriculasVisiveis", is(false)))
			.andExpect(jsonPath("$.itens[*].matricula", everyItem(startsWith("••••"))))
			.andExpect(jsonPath("$.itens[0].matricula", is("••••001")));
	}

	@Test
	void exibicaoDeMatriculasCompletasFicaNaAuditoria() throws Exception {
		long antes = eventos("ACESSO_DADO_PESSOAL");
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.with(token("sub-comercial"))).andExpect(status().isOk());
		mvc.perform(
				get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString()).with(token("sub-leitura")))
			.andExpect(status().isOk());
		assertThat(eventos("ACESSO_DADO_PESSOAL")).isEqualTo(antes + 1);
	}

	@Test
	void paginaPorCursorOpaco() throws Exception {
		var primeira = mvc
			.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
				.param("limite", "2")
				.with(token("sub-leitura")))
			.andExpect(jsonPath("$.itens", hasSize(2)))
			.andReturn();
		var cursor = json.readTree(primeira.getResponse().getContentAsString()).get("proximo").asString();
		assertThat(cursor).doesNotContain("2001002");

		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.param("limite", "2")
			.param("cursor", cursor)
			.with(token("sub-leitura"))).andExpect(jsonPath("$.itens[*].matricula", contains("••••003", "••••004")));
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.param("limite", "10")
			.with(token("sub-leitura"))).andExpect(jsonPath("$.proximo", nullValue()));
	}

	@Test
	void filtraPorSituacaoEMatricula() throws Exception {
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.param("situacao", "CORTADO")
			.with(token("sub-comercial")))
			.andExpect(jsonPath("$.itens[*].matricula", contains("2001001")))
			// os totais das abas não dependem da aba escolhida
			.andExpect(jsonPath("$.totais.todos", is(5)));
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", t.cidadeA().toString())
			.param("matricula", "2001004")
			.with(token("sub-comercial"))).andExpect(jsonPath("$.itens[*].matricula", contains("2001004")));
	}

	@Test
	void entradaInvalidaRetorna400EOutroTenant404() throws Exception {
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("matricula", "20%").with(token("sub-comercial")))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("limite", "5000").with(token("sub-comercial")))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/imoveis/sem-ligacao").param("unidade", outro.cidadeA().toString())
			.with(token("sub-comercial"))).andExpect(status().isNotFound());
	}

	@Test
	void exportaCsvAuditado() throws Exception {
		long antes = eventos("RELATORIO_EXPORTADO");
		var resposta = mvc
			.perform(get("/api/v1/imoveis/sem-ligacao/exportacao.csv").param("unidade", t.cidadeA().toString())
				.param("competencia", "2026-08")
				.with(token("sub-comercial")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))
			.andExpect(header().string("Content-Disposition", startsWith("attachment")))
			.andReturn();
		var csv = new String(resposta.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿matricula;unidade;situacao;")
			.contains("2001001;")
			.contains("Cortado;1;2026-07;2026-08");
		assertThat(csv.lines()).hasSize(6);
		assertThat(eventos("RELATORIO_EXPORTADO")).isEqualTo(antes + 1);
	}

	@Test
	void exportarExigePermissao() throws Exception {
		mvc.perform(get("/api/v1/imoveis/sem-ligacao/exportacao.csv").param("unidade", t.cidadeA().toString())
			.with(token("sub-leitura"))).andExpect(status().isForbidden());
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
