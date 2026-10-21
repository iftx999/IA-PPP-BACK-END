package dev.aurora.metas.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MetasApiTest.Relogio.class })
class MetasApiTest {

	/** "Hoje" fixo em 15/08/2026: a meta da concessão (até dez/2026) está vigente. */
	@TestConfiguration
	static class Relogio {

		@Bean
		@Primary
		Clock relogioDeTeste() {
			return Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
		}

	}

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Metas");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarUsuario(t, "sub-gestor", "gestor@metas.teste", t.grupo());
		DadosDeTeste.criarUsuario(t, "sub-cidade-a", "cidade@metas.teste", t.cidadeA());
		var leitura = DadosDeTeste.criarPerfil(t, "Leitura", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitura", "leitura@metas.teste", t.grupo(), leitura);
	}

	@Test
	void arvoreComHerancaESituacao() throws Exception {
		mvc.perform(get("/api/v1/metas").with(token("sub-leitura")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.referencia", is("2026-08-15")))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')].propria.valor", t.concessao().toString())
				.value(contains(closeTo(80.0, 0.001))))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')].herdada.unidadeNome", t.setorA1().toString())
				.value(contains("MUNICIPIO " + t.cidadeA())))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')].situacao", t.cidadeB().toString())
				.value(contains("ABAIXO_DO_ALERTA")))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')].situacao", t.grupo().toString())
				.value(contains("SEM_META")));
	}

	@Test
	void criaMetaPropriaEBarraSobreposicao() throws Exception {
		var corpo = """
				{"unidadeId":"%s","valor":90,"alerta":85,"vigenciaInicio":"2026-09-01","vigenciaFim":"2026-12-31"}"""
			.formatted(t.cidadeB());
		long antes = eventos("META_DEFINIDA");
		mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(corpo).with(token("sub-gestor")))
			.andExpect(status().isConflict()); // Cidade B já tem meta sem fim desde 2025

		var setor = """
				{"unidadeId":"%s","valor":95,"alerta":90,"vigenciaInicio":"2026-08-01"}""".formatted(t.setorA1());
		mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(setor).with(token("sub-gestor")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/metas/")));
		assertThat(eventos("META_DEFINIDA")).isEqualTo(antes + 1);

		mvc.perform(get("/api/v1/metas").with(token("sub-gestor")))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')].propria.valor", t.setorA1().toString())
				.value(contains(closeTo(95.0, 0.001))));
	}

	@Test
	void validaRegrasEFormato() throws Exception {
		var alertaMaior = """
				{"unidadeId":"%s","valor":80,"alerta":85,"vigenciaInicio":"2030-01-01"}""".formatted(t.cidadeA());
		mvc.perform(post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON)
			.content(alertaMaior)
			.with(token("sub-gestor"))).andExpect(status().isUnprocessableContent());
		var acimaDeCem = """
				{"unidadeId":"%s","valor":150,"alerta":85,"vigenciaInicio":"2030-01-01"}""".formatted(t.cidadeA());
		mvc.perform(post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON)
			.content(acimaDeCem)
			.with(token("sub-gestor"))).andExpect(status().isBadRequest());
	}

	@Test
	void encerraSemApagarHistorico() throws Exception {
		var corpo = """
				{"unidadeId":"%s","valor":70,"alerta":60,"vigenciaInicio":"2031-01-01"}""".formatted(t.grupo());
		var resposta = mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(corpo).with(token("sub-gestor")))
			.andReturn();
		var id = json.readTree(resposta.getResponse().getContentAsString()).get("id").asString();

		mvc.perform(post("/api/v1/metas/{id}/encerramento", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"fim\":\"2030-12-31\"}")
			.with(token("sub-gestor"))).andExpect(status().isUnprocessableContent());
		mvc.perform(post("/api/v1/metas/{id}/encerramento", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"fim\":\"2031-06-30\"}")
			.with(token("sub-gestor"))).andExpect(status().isNoContent());
		assertThat(eventos("META_ENCERRADA")).isGreaterThanOrEqualTo(1);
	}

	@Test
	void exigePermissaoEEscopo() throws Exception {
		var corpo = """
				{"unidadeId":"%s","valor":90,"alerta":80,"vigenciaInicio":"2032-01-01"}""".formatted(t.cidadeB());
		mvc.perform(
				post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON).content(corpo).with(token("sub-leitura")))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/metas").contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.with(token("sub-cidade-a"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/metas").with(token("sub-cidade-a")))
			.andExpect(jsonPath("$.linhas[?(@.unidadeId == '%s')]", t.cidadeB().toString()).isEmpty())
			.andExpect(jsonPath("$.linhas[0].propria.vigenciaFim", nullValue()));
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
