package dev.aurora.roteirizacao.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.StringJoiner;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D6 (ADR 0048): o agente abre a jornada no app e a rota do dia da equipe é calculada a
 * partir da posição dele e publicada sozinha. Três vistorias numa rua, de oeste para
 * leste; a base fica a oeste, mas o agente começa a leste: a rota sai invertida. Sem
 * Valhalla (linha reta, sem traçado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RotaNaJornadaApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final String CELULAR = "X-Aurora-Dispositivo";

	private static Tenant t;

	private static UUID agente;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Rota na jornada");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-rj", "admin@rj.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-programador-rj", "programador@rj.teste", t.concessao(), gestor);
		var campo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar", "dados_pessoais:ler");
		agente = DadosDeTeste.criarUsuario(t, "sub-agente-rj", "agente@rj.teste", t.cidadeA(), campo);
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute(
					"""
							INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
							VALUES ('%1$s', '2001002', '%2$s', ST_SetSRID(ST_MakePoint(-47.06, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001003', '%2$s', ST_SetSRID(ST_MakePoint(-47.05, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001004', '%2$s', ST_SetSRID(ST_MakePoint(-47.04, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO');
							"""
						.formatted(t.id(), t.setorA1()));
		}
	}

	@Test
	void aRotaSaiDaPosicaoDoAgente() throws Exception {
		var hoje = LocalDate.now(FUSO);
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-programador-rj"))));
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002","2001003","2001004"],"prioridade":"ALTA"}"""
			.formatted(servico(opcoes, "VISTORIA")), "sub-programador-rj").andExpect(status().isCreated());
		var os = new HashMap<String, String>();
		for (var item : ler(mvc.perform(get("/api/v1/ordens").with(token("sub-programador-rj")))).get("itens")) {
			os.put(item.get("matricula").asString(), item.get("id").asString());
		}
		var equipe = equipe();
		postar("/api/v1/ordens/programar",
				"""
						{"ordens":["%s","%s","%s"],"equipeId":"%s","data":"%s"}""".formatted(os.get("2001002"),
						os.get("2001003"), os.get("2001004"), equipe, hoje),
				"sub-programador-rj")
			.andExpect(status().isOk());

		var celular = UUID.randomUUID();
		campo("/api/v1/campo/dispositivo", """
				{"modelo":"Moto G","sistema":"Android 15","versaoApp":"1.0.0"}""", celular).andExpect(status().isOk());
		// Sem jornada aberta, não há rota para o celular; sem celular, nada.
		mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular).with(token("sub-agente-rj")))
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/campo/rota").with(token("sub-agente-rj"))).andExpect(status().isForbidden());

		// O agente começa a leste da última vistoria.
		campo("/api/v1/campo/jornada/inicio", """
				{"id":"%s","ocorridoEm":"%s","posicao":{"latitude":-22.90,"longitude":-47.035,"precisaoM":8}}"""
			.formatted(UUID.randomUUID(), Instant.now()), celular).andExpect(status().isOk());

		var rota = esperarRota(celular);
		assertThat(rota.get("origem").asString()).isEqualTo("JORNADA");
		// A do início da jornada não espera confirmação do agente (ADR 0050).
		assertThat(rota.get("exigeConfirmacao").asBoolean()).isFalse();
		assertThat(rota.get("calculo").asString()).isEqualTo("LINHA_RETA");
		assertThat(rota.get("partidaLongitude").asDouble()).isEqualTo(-47.035);
		assertThat(rota.get("tracado").size()).isZero();
		var paradas = rota.get("paradas");
		assertThat(paradas.size()).isEqualTo(3);
		assertThat(paradas.get(0).get("ordemServicoId").asString()).isEqualTo(os.get("2001004"));
		assertThat(paradas.get(2).get("ordemServicoId").asString()).isEqualTo(os.get("2001002"));
		assertThat(paradas.get(0).get("longitude").asDouble()).isEqualTo(-47.04);

		// Publicada sozinha: o pacote do dia vem na nova ordem.
		mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-agente-rj")))
			.andExpect(jsonPath("$.ordens[?(@.ordem.id == '%s')].ordem.sequenciaRota".formatted(os.get("2001004")))
				.value(1))
			.andExpect(jsonPath("$.ordens[?(@.ordem.id == '%s')].ordem.sequenciaRota".formatted(os.get("2001002")))
				.value(3));

		// Mesma rota: 304 pelo ETag.
		var etag = "\"" + rota.get("roteirizacaoId").asString() + "\"";
		mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular)
			.header("If-None-Match", etag)
			.with(token("sub-agente-rj"))).andExpect(status().isNotModified()).andExpect(header().string("ETag", etag));

		// O programador vê a rota da jornada publicada, pedida em nome do agente.
		mvc.perform(get("/api/v1/roteirizacoes").param("data", hoje.toString()).with(token("sub-programador-rj")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].origem").value("JORNADA"))
			.andExpect(jsonPath("$[0].situacao").value("PUBLICADA"));
		try (var c = BancoDeTeste.comoSuperusuario();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT criada_por, publicada_por FROM roteirizacao WHERE id = '%s'"
					.formatted(rota.get("roteirizacaoId").asString()))) {
			assertThat(r.next()).isTrue();
			assertThat(r.getObject("criada_por", UUID.class)).isEqualTo(agente);
			assertThat(r.getObject("publicada_por", UUID.class)).isEqualTo(agente);
		}
	}

	/** Consulta até a rota da jornada ser publicada (no máximo 30 s). */
	private JsonNode esperarRota(UUID celular) throws Exception {
		var limite = Instant.now().plus(Duration.ofSeconds(30));
		while (true) {
			var resposta = mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular).with(token("sub-agente-rj")))
				.andReturn()
				.getResponse();
			if (resposta.getStatus() == 200) {
				return json.readTree(resposta.getContentAsString());
			}
			assertThat(resposta.getStatus()).isEqualTo(204);
			if (Instant.now().isAfter(limite)) {
				throw new AssertionError("A rota da jornada não foi publicada em 30 s.");
			}
			Thread.sleep(200);
		}
	}

	/** Base a oeste, jornada o dia inteiro (o teste roda a qualquer hora). */
	private String equipe() throws Exception {
		var opcoes = ler(mvc.perform(get("/api/v1/equipes/opcoes").with(token("sub-admin-rj"))));
		String vacuo = null;
		for (var v : opcoes.get("veiculos")) {
			if ("CAMINHAO_VACUO".equals(v.get("codigo").asString())) {
				vacuo = v.get("id").asString();
			}
		}
		var habilidades = new StringJoiner(",");
		opcoes.get("habilidades").forEach(h -> habilidades.add("\"" + h.get("id").asString() + "\""));
		return ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"Equipe da jornada","tipo":"PROPRIA","ativa":true,"membros":["%s"],
						 "operacao":{"jornadaInicio":"00:00","jornadaFim":"23:59","pausaMin":30,"tipoVeiculoId":"%s",
						 "habilidades":[%s],"base":{"latitude":-22.90,"longitude":-47.07}}}""".formatted(t.cidadeA(),
						agente, vacuo, habilidades),
				"sub-admin-rj")
			.andExpect(status().isCreated())).get("id").asString();
	}

	private static String servico(JsonNode opcoes, String codigo) {
		for (var s : opcoes.get("servicos")) {
			if (s.get("codigo").asString().equals(codigo)) {
				return s.get("id").asString();
			}
		}
		throw new AssertionError("Serviço não encontrado: " + codigo);
	}

	private ResultActions campo(String url, String corpo, UUID celular) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.header(CELULAR, celular)
			.with(token("sub-agente-rj")));
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(sub)));
	}

	private JsonNode ler(ResultActions resposta) throws Exception {
		return json.readTree(resposta.andReturn().getResponse().getContentAsString());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
