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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roteirização (J6, ADR 0046), sem Valhalla (linha reta): três vistorias numa rua, em
 * linha, e uma sem localização; a equipe com veículo recebe todas na ordem da rua, a sem
 * veículo nenhuma (RN-23). Recalcular com OS fixada, publicar como programação e
 * descartar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoteirizacaoApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Roteirização");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-rota", "admin@rota.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-programador-rota", "programador@rota.teste", t.concessao(), gestor);
		var campo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar");
		DadosDeTeste.criarUsuario(t, "sub-campo-rota", "campo@rota.teste", t.cidadeA(), campo);
		// 2001002 a 2001004 a cada ~1 km para leste; 2001005 sem localização.
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
	void doCalculoAPublicacao() throws Exception {
		var hoje = LocalDate.now(FUSO);
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-programador-rota"))));
		var vistoria = servico(opcoes, "VISTORIA");
		postar("/api/v1/ordens/imoveis",
				"""
						{"servicoId":"%s","matriculas":["2001002","2001003","2001004","2001005"],"prioridade":"ALTA"}"""
					.formatted(vistoria),
				"sub-programador-rota")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(4));
		var os = new HashMap<String, String>();
		for (var item : ler(mvc.perform(get("/api/v1/ordens").with(token("sub-programador-rota")))).get("itens")) {
			os.put(item.get("matricula").asString(), item.get("id").asString());
		}
		var completa = equipe("Equipe Leste", operacaoCompleta(-47.07, -22.90));
		var aPe = equipe("Equipe a pé", """
				"operacao":{"jornadaInicio":"07:30","jornadaFim":"16:30","pausaMin":60,"habilidades":[],
				 "base":{"latitude":-22.90,"longitude":-47.03}}""");
		var semBase = equipe("Equipe sem base", """
				"operacao":{"jornadaInicio":"07:30","jornadaFim":"16:30","pausaMin":60,"habilidades":[]}""");

		// Candidatas da Cidade A: as quatro planejadas (uma sem local) e as três equipes.
		mvc.perform(get("/api/v1/roteirizacoes/candidatas").param("unidade", t.cidadeA().toString())
			.param("data", hoje.toString())
			.with(token("sub-programador-rota")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ordens.length()").value(4))
			.andExpect(jsonPath("$.ordens[?(@.temLocal == false)]", org.hamcrest.Matchers.hasSize(1)))
			.andExpect(jsonPath("$.truncada").value(false))
			.andExpect(jsonPath("$.equipes.length()").value(3))
			.andExpect(jsonPath("$.equipes[?(@.nome == 'Equipe sem base')].temBase").value(false));

		var pedido = """
				{"unidadeId":"%s","data":"%s","equipes":[%s],"ordens":[%s]}""";
		var ordens = lista(os.get("2001002"), os.get("2001003"), os.get("2001004"), os.get("2001005"));
		// Sem permissão; equipe sem base; data passada.
		postar("/api/v1/roteirizacoes", pedido.formatted(t.cidadeA(), hoje, lista(completa), ordens), "sub-campo-rota")
			.andExpect(status().isForbidden());
		postar("/api/v1/roteirizacoes", pedido.formatted(t.cidadeA(), hoje, lista(completa, semBase), ordens),
				"sub-programador-rota")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Equipe sem base")));
		postar("/api/v1/roteirizacoes", pedido.formatted(t.cidadeA(), hoje.minusDays(1), lista(completa), ordens),
				"sub-programador-rota")
			.andExpect(status().isUnprocessableContent());

		// Prévia: a equipe completa faz as três na ordem da rua; a sem veículo, nenhuma.
		var calculando = ler(
				postar("/api/v1/roteirizacoes", pedido.formatted(t.cidadeA(), hoje, lista(completa, aPe), ordens),
						"sub-programador-rota")
					.andExpect(status().isAccepted())
					.andExpect(jsonPath("$.situacao").value("CALCULANDO")));
		var pronta = esperar(calculando.get("id").asString());
		assertThat(pronta.get("situacao").asString()).isEqualTo("PRONTA");
		assertThat(pronta.get("calculo").asString()).isEqualTo("LINHA_RETA");
		var rotaLeste = rota(pronta, "Equipe Leste");
		assertThat(rotaLeste.get("paradas").size()).isEqualTo(3);
		assertThat(rotaLeste.get("paradas").get(0).get("ordemServicoId").asString()).isEqualTo(os.get("2001002"));
		assertThat(rotaLeste.get("paradas").get(2).get("ordemServicoId").asString()).isEqualTo(os.get("2001004"));
		assertThat(rotaLeste.get("km").asDouble()).isGreaterThan(3);
		assertThat(Instant.parse(rotaLeste.get("inicio").asString()))
			.isEqualTo(hoje.atTime(7, 30).atZone(FUSO).toInstant());
		assertThat(rota(pronta, "Equipe a pé").get("paradas").size()).isZero();
		assertThat(pronta.get("semRota").size()).isEqualTo(1);
		assertThat(pronta.get("semRota").get(0).get("motivo").asString()).isEqualTo("SEM_LOCAL");

		// Recalcular com a mais distante fixada no começo.
		var recalculando = ler(postar("/api/v1/roteirizacoes/" + pronta.get("id").asString() + "/recalcular", """
				{"fixadas":[{"equipeId":"%s","ordens":["%s"]}]}""".formatted(completa, os.get("2001004")),
				"sub-programador-rota")
			.andExpect(status().isAccepted()));
		var recalculada = esperar(recalculando.get("id").asString());
		assertThat(recalculada.get("anteriorId").asString()).isEqualTo(pronta.get("id").asString());
		var primeira = rota(recalculada, "Equipe Leste").get("paradas").get(0);
		assertThat(primeira.get("ordemServicoId").asString()).isEqualTo(os.get("2001004"));
		assertThat(primeira.get("fixada").asBoolean()).isTrue();

		// Publicar a recalculada: as três ficam programadas para a equipe, na sequência
		// da rota.
		var idRecalculada = recalculada.get("id").asString();
		postar("/api/v1/roteirizacoes/" + idRecalculada + "/publicar", "", "sub-programador-rota")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("PUBLICADA"));
		postar("/api/v1/roteirizacoes/" + idRecalculada + "/publicar", "", "sub-programador-rota")
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/ordens/{id}", os.get("2001004")).with(token("sub-programador-rota")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROGRAMADA"))
			.andExpect(jsonPath("$.equipeId").value(completa))
			.andExpect(jsonPath("$.sequenciaRota").value(1));
		mvc.perform(get("/api/v1/ordens/{id}", os.get("2001005")).with(token("sub-programador-rota")))
			.andExpect(jsonPath("$.status").value("PLANEJADA"));

		// J7: reotimizar hoje parte da posição (sem jornada aberta, da base) e pega as
		// três programadas.
		var reotimizando = ler(postar("/api/v1/roteirizacoes/reotimizar", """
				{"unidadeId":"%s","equipes":["%s"]}""".formatted(t.cidadeA(), completa), "sub-programador-rota")
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.origem").value("REOTIMIZACAO")));
		var reotimizada = esperar(reotimizando.get("id").asString());
		assertThat(reotimizada.get("situacao").asString()).isEqualTo("PRONTA");
		assertThat(rota(reotimizada, "Equipe Leste").get("paradas").size()).isEqualTo(3);
		postar("/api/v1/roteirizacoes/reotimizar", """
				{"unidadeId":"%s","equipes":["%s"]}""".formatted(t.cidadeA(), aPe), "sub-programador-rota")
			.andExpect(status().isUnprocessableContent());

		// A prévia anterior pode ser descartada; a publicada, não.
		postar("/api/v1/roteirizacoes/" + pronta.get("id").asString() + "/descartar", "", "sub-programador-rota")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("DESCARTADA"));
		postar("/api/v1/roteirizacoes/" + idRecalculada + "/descartar", "", "sub-programador-rota")
			.andExpect(status().isConflict());
		mvc.perform(get("/api/v1/roteirizacoes").param("data", hoje.toString()).with(token("sub-programador-rota")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[1].situacao").value("PUBLICADA"));
	}

	/** Consulta até o cálculo terminar (no máximo 30 s). */
	private JsonNode esperar(String id) throws Exception {
		var limite = Instant.now().plus(Duration.ofSeconds(30));
		while (true) {
			var atual = ler(mvc.perform(get("/api/v1/roteirizacoes/{id}", id).with(token("sub-programador-rota")))
				.andExpect(status().isOk()));
			if (!"CALCULANDO".equals(atual.get("situacao").asString()) || Instant.now().isAfter(limite)) {
				return atual;
			}
			Thread.sleep(200);
		}
	}

	private static JsonNode rota(JsonNode roteirizacao, String equipe) {
		for (var rota : roteirizacao.get("rotas")) {
			if (rota.get("equipeNome").asString().equals(equipe)) {
				return rota;
			}
		}
		throw new AssertionError("Rota não encontrada: " + equipe);
	}

	private String equipe(String nome, String operacao) throws Exception {
		return ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"%s","tipo":"PROPRIA","ativa":true,"membros":[],%s}"""
					.formatted(t.cidadeA(), nome, operacao),
				"sub-admin-rota")
			.andExpect(status().isCreated())).get("id").asString();
	}

	/** Todas as habilidades e caminhão de vácuo (atende moto, carro e vácuo). */
	private String operacaoCompleta(double longitude, double latitude) throws Exception {
		var opcoes = ler(mvc.perform(get("/api/v1/equipes/opcoes").with(token("sub-admin-rota"))));
		String vacuo = null;
		for (var v : opcoes.get("veiculos")) {
			if ("CAMINHAO_VACUO".equals(v.get("codigo").asString())) {
				vacuo = v.get("id").asString();
			}
		}
		var habilidades = new StringJoiner(",");
		opcoes.get("habilidades").forEach(h -> habilidades.add("\"" + h.get("id").asString() + "\""));
		return """
				"operacao":{"jornadaInicio":"07:30","jornadaFim":"16:30","pausaMin":60,"tipoVeiculoId":"%s",
				 "habilidades":[%s],"base":{"latitude":%s,"longitude":%s}}""".formatted(vacuo, habilidades, latitude,
				longitude);
	}

	private static String lista(String... ids) {
		var junta = new StringJoiner(",");
		for (var id : ids) {
			junta.add("\"" + id + "\"");
		}
		return junta.toString();
	}

	private static String servico(JsonNode opcoes, String codigo) {
		for (var s : opcoes.get("servicos")) {
			if (s.get("codigo").asString().equals(codigo)) {
				return s.get("id").asString();
			}
		}
		throw new AssertionError("Serviço não encontrado: " + codigo);
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
