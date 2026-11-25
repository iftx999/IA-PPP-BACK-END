package dev.aurora.campanhas.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.campanhas.integracao.EnvioDeSms;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Estou chegando" (ADR 0052): a concessionária manda os celulares em CSV (cifrados no
 * banco, nunca devolvidos), o pacote do app diz quais OS têm contato e o agente avisa o
 * morador uma vez por OS. O SMS sai por um provedor de teste que guarda o que enviaria.
 */
@SpringBootTest(properties = "aurora.sms.provedor=teste")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AvisoDeChegadaApiTest.ProvedorDeTeste.class })
class AvisoDeChegadaApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final String CELULAR = "X-Aurora-Dispositivo";

	private static final List<Map.Entry<String, String>> ENVIADOS = new ArrayList<>();

	private static boolean falhar;

	private static Tenant t;

	private static UUID agente;

	@TestConfiguration
	static class ProvedorDeTeste {

		@Bean
		EnvioDeSms smsDeTeste() {
			return (telefone, texto) -> {
				if (falhar) {
					throw new IllegalStateException("provedor fora do ar");
				}
				ENVIADOS.add(Map.entry(telefone, texto));
				return new EnvioDeSms.Resultado(true, "teste", null);
			};
		}

	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Aviso de chegada");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-ac", "admin@ac.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "logradouro:gerenciar",
				"dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-gestor-ac", "gestor@ac.teste", t.concessao(), gestor);
		var campo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar", "dados_pessoais:ler");
		agente = DadosDeTeste.criarUsuario(t, "sub-agente-ac", "agente@ac.teste", t.cidadeA(), campo);
		var logradouro = UUID.randomUUID();
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute(
					"""
							INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
							VALUES ('%1$s', '2001002', '%2$s', ST_SetSRID(ST_MakePoint(-47.06, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001003', '%2$s', ST_SetSRID(ST_MakePoint(-47.05, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001004', '%2$s', ST_SetSRID(ST_MakePoint(-47.04, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO');
							INSERT INTO logradouro (id, tenant_id, unidade_id, codigo, tipo, nome, nome_busca)
							VALUES ('%3$s', '%1$s', '%4$s', 'L-1', 'Rua', 'Rua das Flores', 'rua das flores');
							INSERT INTO imovel_endereco (tenant_id, codigo_origem, unidade_id, logradouro_id, numero, numero_inteiro)
							VALUES ('%1$s', '2001002', '%2$s', '%3$s', '10', 10),
							       ('%1$s', '2001003', '%2$s', '%3$s', '11', 11),
							       ('%1$s', '2001004', '%2$s', '%3$s', '12', 12);
							"""
						.formatted(t.id(), t.setorA1(), logradouro, t.cidadeA()));
		}
	}

	@Test
	void doArquivoDaConcessionariaAoSmsDoMorador() throws Exception {
		// 1. Importação: celular com e sem pontuação e 55; fixo e imóvel sem endereço
		// recusados.
		var csv = """
				matricula;telefone
				2001002;(19) 99876-5432
				2001003;1933334444
				2001004;
				9999999;19998765432
				2001003;55 19 98765-1111
				""";
		mvc.perform(multipart("/api/v1/contatos-dos-imoveis").file(arquivo(csv)).with(token("sub-agente-ac")))
			.andExpect(status().isForbidden());
		var importacao = mvc
			.perform(multipart("/api/v1/contatos-dos-imoveis").file(arquivo(csv)).with(token("sub-gestor-ac")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linhas").value(5))
			.andExpect(jsonPath("$.gravados").value(2))
			.andExpect(jsonPath("$.rejeitados").value(2))
			.andExpect(jsonPath("$.rejeicoes[0].linha").value(3))
			.andExpect(jsonPath("$.rejeicoes[1].linha").value(5))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(importacao).doesNotContain("99876", "5432");
		mvc.perform(get("/api/v1/contatos-dos-imoveis").with(token("sub-gestor-ac")))
			.andExpect(jsonPath("$.total").value(2));
		try (var c = BancoDeTeste.comoSuperusuario();
				var s = c.createStatement();
				var r = s.executeQuery(
						"SELECT telefone_cifrado, final_telefone FROM imovel_contato WHERE codigo_origem = '2001002'")) {
			assertThat(r.next()).isTrue();
			assertThat(r.getString("telefone_cifrado")).doesNotContain("99876");
			assertThat(r.getString("final_telefone")).isEqualTo("5432");
		}

		// 2. Programação e jornada.
		var hoje = LocalDate.now(FUSO);
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-gestor-ac"))));
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002","2001003","2001004"],"prioridade":"ALTA"}"""
			.formatted(servico(opcoes, "VISTORIA")), "sub-gestor-ac").andExpect(status().isCreated());
		var os = new HashMap<String, String>();
		for (var item : ler(mvc.perform(get("/api/v1/ordens").with(token("sub-gestor-ac")))).get("itens")) {
			os.put(item.get("matricula").asString(), item.get("id").asString());
		}
		var equipe = equipe();
		postar("/api/v1/ordens/programar",
				"""
						{"ordens":["%s","%s","%s"],"equipeId":"%s","data":"%s"}""".formatted(os.get("2001002"),
						os.get("2001003"), os.get("2001004"), equipe, hoje),
				"sub-gestor-ac")
			.andExpect(status().isOk());
		var celular = UUID.randomUUID();
		campo("/api/v1/campo/dispositivo", """
				{"modelo":"Moto G","sistema":"Android 15","versaoApp":"1.0.0"}""", celular).andExpect(status().isOk());
		campo("/api/v1/campo/jornada/inicio", """
				{"id":"%s","ocorridoEm":"%s","posicao":{"latitude":-22.90,"longitude":-47.07,"precisaoM":8}}"""
			.formatted(UUID.randomUUID(), Instant.now()), celular).andExpect(status().isOk());

		// 3. O pacote diz quais OS têm contato, sem o número.
		var pacote = mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-agente-ac")))
			.andExpect(
					jsonPath("$.ordens[?(@.ordem.id == '%s')].avisoDeChegada".formatted(os.get("2001002"))).value(true))
			.andExpect(jsonPath("$.ordens[?(@.ordem.id == '%s')].avisoDeChegada".formatted(os.get("2001004")))
				.value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(pacote).doesNotContain("99876", "+55");

		// 4. Sem contato: recusado. Com contato: sai o SMS, sem nome de ninguém.
		aviso(celular, os.get("2001004"), UUID.randomUUID(), 10).andExpect(status().isUnprocessableEntity());
		var id = UUID.randomUUID();
		aviso(celular, os.get("2001002"), id, 10).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("ENVIADO"))
			.andExpect(jsonPath("$.minutos").value(10));
		assertThat(ENVIADOS).hasSize(1);
		assertThat(ENVIADOS.getFirst().getKey()).isEqualTo("+5519998765432");
		assertThat(ENVIADOS.getFirst().getValue()).contains("a caminho", "cerca de 10 minutos", "Vistoria")
			.doesNotContain("agente@", "Equipe do aviso");

		// O mesmo toque reenviado devolve o mesmo aviso; outro aviso para a OS é
		// recusado.
		aviso(celular, os.get("2001002"), id, 10).andExpect(status().isOk());
		aviso(celular, os.get("2001002"), UUID.randomUUID(), 5).andExpect(status().isConflict());
		assertThat(ENVIADOS).hasSize(1);
		mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-agente-ac")))
			.andExpect(jsonPath("$.ordens[?(@.ordem.id == '%s')].avisadoEm".formatted(os.get("2001002"))).isNotEmpty());

		// 5. Provedor fora do ar: fica registrado como FALHOU e dá para tentar de novo.
		falhar = true;
		aviso(celular, os.get("2001003"), UUID.randomUUID(), 15).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("FALHOU"));
		falhar = false;
		aviso(celular, os.get("2001003"), UUID.randomUUID(), 15).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("ENVIADO"));
		assertThat(ENVIADOS.getLast().getKey()).isEqualTo("+5519987651111");

		// 6. A auditoria registra o aviso sem o telefone.
		try (var c = BancoDeTeste.comoSuperusuario();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT detalhes::text FROM evento_auditoria WHERE tipo = 'AVISO_DE_CHEGADA'")) {
			var algum = false;
			while (r.next()) {
				algum = true;
				assertThat(r.getString(1)).doesNotContain("99876", "+55");
			}
			assertThat(algum).isTrue();
		}
	}

	/**
	 * Equipe com todas as habilidades e caminhão (atende qualquer serviço do catálogo,
	 * RN-23).
	 */
	private String equipe() throws Exception {
		var opcoes = ler(mvc.perform(get("/api/v1/equipes/opcoes").with(token("sub-admin-ac"))));
		String vacuo = null;
		for (var v : opcoes.get("veiculos")) {
			if ("CAMINHAO_VACUO".equals(v.get("codigo").asString())) {
				vacuo = v.get("id").asString();
			}
		}
		var habilidades = new java.util.StringJoiner(",");
		opcoes.get("habilidades").forEach(h -> habilidades.add("\"" + h.get("id").asString() + "\""));
		return ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"Equipe do aviso","tipo":"PROPRIA","ativa":true,"membros":["%s"],
						 "operacao":{"jornadaInicio":"00:00","jornadaFim":"23:59","pausaMin":0,"tipoVeiculoId":"%s",
						 "habilidades":[%s],"base":{"latitude":-22.90,"longitude":-47.07}}}""".formatted(t.cidadeA(),
						agente, vacuo, habilidades),
				"sub-admin-ac")
			.andExpect(status().isCreated())).get("id").asString();
	}

	private ResultActions aviso(UUID celular, String os, UUID id, int minutos) throws Exception {
		return campo("/api/v1/campo/ordens/" + os + "/aviso-de-chegada", """
				{"id":"%s","minutos":%d,"ocorridoEm":"%s"}""".formatted(id, minutos, Instant.now()), celular);
	}

	private static MockMultipartFile arquivo(String csv) {
		return new MockMultipartFile("arquivo", "contatos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
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
			.with(token("sub-agente-ac")));
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
