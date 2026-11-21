package dev.aurora.operacao.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.operacao.service.DetectorDeReotimizacao;
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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * J12 e J7 (ADR 0049): um dia de campo com quatro vistorias de uma equipe — uma
 * executada, uma paralisada há 35 min, uma devolvida e uma programada — e uma emergencial
 * nova. O mapa do dia mostra a equipe e as OS; o detector abre uma sugestão por gatilho
 * (e não repete); aceitar junta as abertas numa reotimização; os indicadores medem
 * tempos, motivos e produtividade. Sem Valhalla (linha reta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OperacaoApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final String CELULAR = "X-Aurora-Dispositivo";

	private static Tenant t;

	private static UUID agente;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	DetectorDeReotimizacao detector;

	@Autowired
	ContextosDoSistema contextos;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Operação do dia");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-op", "admin@op.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-programador-op", "programador@op.teste", t.concessao(), gestor);
		var campo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar", "dados_pessoais:ler");
		agente = DadosDeTeste.criarUsuario(t, "sub-agente-op", "agente@op.teste", t.cidadeA(), campo);
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute(
					"""
							INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
							VALUES ('%1$s', '2001002', '%2$s', ST_SetSRID(ST_MakePoint(-47.06, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001003', '%2$s', ST_SetSRID(ST_MakePoint(-47.05, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001004', '%2$s', ST_SetSRID(ST_MakePoint(-47.04, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001005', '%2$s', ST_SetSRID(ST_MakePoint(-47.03, -22.90), 4674), 'GEOCODIFICADA', 'ENDERECO'),
							       ('%1$s', '2001006', '%2$s', ST_SetSRID(ST_MakePoint(-47.045, -22.905), 4674), 'GEOCODIFICADA', 'ENDERECO');
							"""
						.formatted(t.id(), t.setorA1()));
		}
	}

	@Test
	void umDiaDeCampo() throws Exception {
		var hoje = LocalDate.now(FUSO);
		var agora = Instant.now();
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-programador-op"))));
		var vistoria = servico(opcoes, "VISTORIA");
		var motivos = opcoes.get("motivos");
		var chuva = motivo(motivos, "PARALISACAO", "CHUVA");
		var semEndereco = motivo(motivos, "DEVOLUCAO", "ENDERECO_NAO_ENCONTRADO");
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002","2001003","2001004","2001005"],"prioridade":"ALTA"}"""
			.formatted(vistoria), "sub-programador-op").andExpect(status().isCreated());
		var os = ordens();
		var equipe = equipe();
		postar("/api/v1/ordens/programar",
				"""
						{"ordens":["%s","%s","%s","%s"],"equipeId":"%s","data":"%s"}""".formatted(os.get("2001002"),
						os.get("2001003"), os.get("2001004"), os.get("2001005"), equipe, hoje),
				"sub-programador-op")
			.andExpect(status().isOk());

		// Sem jornada: a equipe aparece no mapa sem posição nem atraso.
		mvc.perform(
				get("/api/v1/operacao/dia").param("unidade", t.cidadeA().toString()).with(token("sub-programador-op")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.equipes[?(@.id == '%s')].emJornada".formatted(equipe)).value(false))
			.andExpect(jsonPath("$.ordens", org.hamcrest.Matchers.hasSize(4)));

		// Jornada aberta há 1 h, a leste; a rota da jornada sai publicada.
		var celular = UUID.randomUUID();
		campo("/api/v1/campo/dispositivo", """
				{"modelo":"Moto G","sistema":"Android 15","versaoApp":"1.0.0"}""", celular).andExpect(status().isOk());
		campo("/api/v1/campo/jornada/inicio", """
				{"id":"%s","ocorridoEm":"%s","posicao":{"latitude":-22.90,"longitude":-47.065,"precisaoM":8}}"""
			.formatted(UUID.randomUUID(), agora.minus(min(60))), celular).andExpect(status().isOk());
		esperarRota(celular);

		// Executada (5 min de deslocamento, 10 de serviço), paralisada há 35 min,
		// devolvida.
		var foto = UUID.randomUUID();
		eventos(celular, agora, evento(os.get("2001002"), 1, "INICIAR", "", agora.minus(min(55))),
				evento(os.get("2001002"), 2, "ENCERRAR", """
						,"resultado":"EXECUTADA","valores":{"viavel":true,"abaixo_da_rede":false,"distancia_rede":3,
						 "foto":["%s"]}""".formatted(foto), agora.minus(min(45))),
				evento(os.get("2001003"), 1, "INICIAR", "", agora.minus(min(40))),
				evento(os.get("2001003"), 2, "PARALISAR", ",\"motivoId\":\"" + chuva + "\"", agora.minus(min(35))),
				evento(os.get("2001004"), 1, "DEVOLVER", ",\"motivoId\":\"" + semEndereco + "\"", agora.minus(min(34))))
			.andExpect(jsonPath("$.eventos[*].situacao")
				.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("APLICADO"))));
		campo("/api/v1/campo/posicoes", """
				{"posicoes":[{"momento":"%s","latitude":-22.90,"longitude":-47.06,"precisaoM":10},
				             {"momento":"%s","latitude":-22.90,"longitude":-47.05,"precisaoM":10}]}"""
			.formatted(agora.minus(min(50)), agora.minus(min(38))), celular).andExpect(status().isOk());

		// Emergencial nova na área, sem equipe.
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001006"],"prioridade":"EMERGENCIAL"}""".formatted(vistoria),
				"sub-programador-op")
			.andExpect(status().isCreated());
		var emergencial = ordens().get("2001006");

		// A rota publicada é deslocada 3 h para trás: a equipe fica atrasada.
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("""
					UPDATE rota_parada p
					   SET chegada_prevista = chegada_prevista - interval '3 hours',
					       saida_prevista = saida_prevista - interval '3 hours'
					  FROM rota r WHERE r.id = p.rota_id AND r.equipe_id = '%s'""".formatted(equipe));
		}

		var mapa = ler(mvc
			.perform(get("/api/v1/operacao/dia").param("unidade", t.cidadeA().toString())
				.with(token("sub-programador-op")))
			.andExpect(status().isOk()));
		var naRua = encontrar(mapa.get("equipes"), "id", equipe);
		assertThat(naRua.get("emJornada").asBoolean()).isTrue();
		assertThat(naRua.get("posicao").get("longitude").asDouble()).isEqualTo(-47.05);
		assertThat(naRua.get("atrasoMin").asInt()).isGreaterThan(60);
		assertThat(encontrar(mapa.get("ordens"), "id", emergencial).get("prioridade").asString())
			.isEqualTo("EMERGENCIAL");
		assertThat(encontrar(mapa.get("ordens"), "id", os.get("2001003")).get("status").asString())
			.isEqualTo("PARALISADA");
		assertThat(encontrar(mapa.get("ordens"), "id", os.get("2001002")).get("ponto").get("longitude").asDouble())
			.isEqualTo(-47.06);

		// O detector abre uma sugestão por gatilho e não repete na passada seguinte.
		var passada = detectar();
		assertThat(passada.abertas()).isEqualTo(4);
		assertThat(detectar().abertas()).isZero();
		var sugestoes = ler(mvc
			.perform(get("/api/v1/operacao/sugestoes").param("unidade", t.cidadeA().toString())
				.with(token("sub-programador-op")))
			.andExpect(status().isOk()));
		var porGatilho = new HashMap<String, JsonNode>();
		sugestoes.forEach(s -> porGatilho.put(s.get("gatilho").asString(), s));
		assertThat(porGatilho).containsOnlyKeys("EMERGENCIAL", "PARALISACAO_LONGA", "DEVOLUCAO", "ATRASO");
		assertThat(porGatilho.get("PARALISACAO_LONGA").get("descricao").asString()).contains("Chuva");
		assertThat(porGatilho.get("DEVOLUCAO").get("osId").asString()).isEqualTo(os.get("2001004"));
		assertThat(porGatilho.get("EMERGENCIAL").get("equipeId").isNull()).isTrue();
		assertThat(porGatilho.get("ATRASO").get("atrasoMin").asInt()).isGreaterThanOrEqualTo(30);
		mvc.perform(
				get("/api/v1/operacao/dia").param("unidade", t.cidadeA().toString()).with(token("sub-programador-op")))
			.andExpect(jsonPath("$.sugestoesAbertas").value(4));
		// Avisos da web: o resumo do escopo inteiro.
		mvc.perform(get("/api/v1/operacao/sugestoes/resumo").with(token("sub-programador-op")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.abertas").value(4))
			.andExpect(jsonPath("$.recentes", org.hamcrest.Matchers.hasSize(4)));

		// Ignorar é uma decisão só; a mesma não é decidida duas vezes.
		var atraso = porGatilho.get("ATRASO").get("id").asString();
		postar("/api/v1/operacao/sugestoes/" + atraso + "/ignorar", "", "sub-programador-op").andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("IGNORADA"));
		postar("/api/v1/operacao/sugestoes/" + atraso + "/ignorar", "", "sub-programador-op")
			.andExpect(status().isConflict());

		// Aceitar a emergencial junta as abertas da unidade numa reotimização com ela.
		var aceita = ler(
				postar("/api/v1/operacao/sugestoes/" + porGatilho.get("EMERGENCIAL").get("id").asString() + "/aceitar",
						"", "sub-programador-op")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.situacao").value("ACEITA")));
		var roteirizacao = aceita.get("roteirizacaoId").asString();
		var depois = ler(mvc.perform(get("/api/v1/operacao/sugestoes").param("unidade", t.cidadeA().toString())
			.with(token("sub-programador-op"))));
		for (var s : depois) {
			if (!"ATRASO".equals(s.get("gatilho").asString())) {
				assertThat(s.get("situacao").asString()).isEqualTo("ACEITA");
				assertThat(s.get("roteirizacaoId").asString()).isEqualTo(roteirizacao);
			}
		}
		var calculada = esperarCalculo(roteirizacao);
		assertThat(calculada.get("origem").asString()).isEqualTo("REOTIMIZACAO");
		var naRota = new StringJoiner(",");
		calculada.get("rotas")
			.forEach(r -> r.get("paradas").forEach(p -> naRota.add(p.get("ordemServicoId").asString())));
		calculada.get("semRota").forEach(p -> naRota.add(p.get("ordemServicoId").asString()));
		assertThat(naRota.toString()).contains(emergencial, os.get("2001005"), os.get("2001003"))
			.doesNotContain(os.get("2001004"));

		// Publicar a reotimização de uma equipe substitui a rota da jornada dela, mas não
		// a
		// publicada de outra equipe da mesma unidade (que seguiria sem rota no app).
		var jornada = "";
		for (var r : ler(mvc
			.perform(get("/api/v1/roteirizacoes").param("data", hoje.toString()).with(token("sub-programador-op"))))) {
			if ("JORNADA".equals(r.get("origem").asString())) {
				jornada = r.get("id").asString();
			}
		}
		var outraEquipe = ler(
				postar("/api/v1/equipes",
						"""
								{"unidadeId":"%s","nome":"Outra equipe","tipo":"PROPRIA","ativa":true,"membros":[]}"""
							.formatted(t.cidadeA()),
						"sub-admin-op")
					.andExpect(status().isCreated()))
			.get("id")
			.asString();
		var daOutra = UUID.randomUUID();
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("""
					INSERT INTO roteirizacao (id, tenant_id, unidade_id, data, origem, situacao, calculo, criada_por,
					                          concluida_em, publicada_em, publicada_por)
					VALUES ('%1$s', '%2$s', '%3$s', '%4$s', 'PREVIA', 'PUBLICADA', 'LINHA_RETA', '%5$s', now(),
					        now() - interval '2 hours', '%5$s');
					INSERT INTO rota (id, tenant_id, roteirizacao_id, equipe_id, inicio, fim_previsto, km,
					                  minutos_deslocamento, minutos_servico)
					VALUES (gen_random_uuid(), '%2$s', '%1$s', '%6$s', now(), now(), 0, 0, 0);""".formatted(daOutra,
					t.id(), calculada.get("unidadeId").asString(), hoje, agente, outraEquipe));
		}
		postar("/api/v1/roteirizacoes/" + roteirizacao + "/publicar", "", "sub-programador-op")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("PUBLICADA"));
		mvc.perform(get("/api/v1/roteirizacoes/" + jornada).with(token("sub-programador-op")))
			.andExpect(jsonPath("$.situacao").value("DESCARTADA"));
		mvc.perform(get("/api/v1/roteirizacoes/" + daOutra).with(token("sub-programador-op")))
			.andExpect(jsonPath("$.situacao").value("PUBLICADA"));
		// Publicada, a emergencial entrou na rota: a sugestão dela não volta.
		assertThat(detectar().abertas()).isZero();

		// ADR 0050: publicada no meio da jornada, a nova ordem espera o agente confirmar
		// no app.
		var nova = ler(mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular).with(token("sub-agente-op")))
			.andExpect(status().isOk()));
		assertThat(nova.get("roteirizacaoId").asString()).isEqualTo(roteirizacao);
		assertThat(nova.get("exigeConfirmacao").asBoolean()).isTrue();
		assertThat(nova.get("confirmadaEm").isNull()).isTrue();
		mvc.perform(
				get("/api/v1/operacao/dia").param("unidade", t.cidadeA().toString()).with(token("sub-programador-op")))
			.andExpect(jsonPath("$.equipes[?(@.id == '%s')].rotaAguardandoConfirmacao".formatted(equipe)).value(true));
		// A da jornada já foi substituída: confirmar ela é recusado.
		campo("/api/v1/campo/rota/confirmacao", """
				{"roteirizacaoId":"%s","confirmadaEm":"%s"}""".formatted(jornada, Instant.now()), celular)
			.andExpect(status().isConflict());
		var confirmada = ler(campo("/api/v1/campo/rota/confirmacao", """
				{"roteirizacaoId":"%s","confirmadaEm":"%s"}""".formatted(roteirizacao, Instant.now().minusSeconds(30)),
				celular)
			.andExpect(status().isOk()));
		// Repetir (a fila reenviou) não muda a primeira confirmação.
		campo("/api/v1/campo/rota/confirmacao", """
				{"roteirizacaoId":"%s","confirmadaEm":"%s"}""".formatted(roteirizacao, Instant.now()), celular)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.confirmadaEm").value(confirmada.get("confirmadaEm").asString()));
		mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular).with(token("sub-agente-op")))
			.andExpect(jsonPath("$.confirmadaEm").value(confirmada.get("confirmadaEm").asString()));
		mvc.perform(
				get("/api/v1/operacao/dia").param("unidade", t.cidadeA().toString()).with(token("sub-programador-op")))
			.andExpect(jsonPath("$.equipes[?(@.id == '%s')].rotaAguardandoConfirmacao".formatted(equipe)).value(false))
			.andExpect(jsonPath("$.equipes[?(@.id == '%s')].rotaConfirmadaEm".formatted(equipe)).isNotEmpty());

		// Indicadores do dia (a emergencial, publicada, já conta).
		var ind = ler(mvc
			.perform(get("/api/v1/operacao/indicadores").param("unidade", t.cidadeA().toString())
				.param("de", hoje.toString())
				.param("ate", hoje.toString())
				.with(token("sub-programador-op")))
			.andExpect(status().isOk()));
		var resumo = ind.get("resumo");
		assertThat(resumo.get("ordens").asInt()).isEqualTo(5);
		assertThat(resumo.get("executadas").asInt()).isEqualTo(1);
		assertThat(resumo.get("devolvidas").asInt()).isEqualTo(1);
		assertThat(resumo.get("noPrazo").asInt()).isEqualTo(1);
		assertThat(resumo.get("execucaoMediaMin").asDouble()).isCloseTo(10, within(0.1));
		assertThat(resumo.get("deslocamentoMedioMin").asDouble()).isCloseTo(5, within(0.1));
		assertThat(resumo.get("km").asDouble()).isCloseTo(1.02, within(0.05));
		var tempo = ind.get("tempos").get(0);
		assertThat(tempo.get("amostras").asInt()).isEqualTo(1);
		assertThat(tempo.get("padraoMedioMin").asDouble()).isEqualTo(20);
		assertThat(tempo.get("razaoMediana").asDouble()).isCloseTo(0.5, within(0.01));
		assertThat(tempo.get("tempoSugerido").isNull()).isTrue();
		assertThat(ind.get("motivos").toString()).contains("CHUVA", "ENDERECO_NAO_ENCONTRADO");
		var produtividade = encontrar(ind.get("equipes"), "equipeId", equipe);
		assertThat(produtividade.get("executadas").asInt()).isEqualTo(1);
		assertThat(produtividade.get("horasDeJornada").asDouble()).isCloseTo(1, within(0.05));
		assertThat(ind.get("prazos").get(0).get("noPrazo").asInt()).isEqualTo(1);

		// Período inválido; agente de campo não acompanha a operação.
		mvc.perform(get("/api/v1/operacao/indicadores").param("de", hoje.toString())
			.param("ate", hoje.minusDays(1).toString())
			.with(token("sub-programador-op"))).andExpect(status().isUnprocessableEntity());
		mvc.perform(get("/api/v1/operacao/indicadores").param("de", hoje.minusDays(100).toString())
			.param("ate", hoje.toString())
			.with(token("sub-programador-op"))).andExpect(status().isUnprocessableEntity());
		mvc.perform(get("/api/v1/operacao/dia").with(token("sub-agente-op"))).andExpect(status().isForbidden());
	}

	private DetectorDeReotimizacao.Passada detectar() throws Exception {
		var contexto = contextos.doTenant(t.id()).orElseThrow();
		return ContextoAcesso.executar(contexto, detector::detectar);
	}

	private Map<String, String> ordens() throws Exception {
		var os = new HashMap<String, String>();
		for (var item : ler(mvc.perform(get("/api/v1/ordens").with(token("sub-programador-op")))).get("itens")) {
			os.put(item.get("matricula").asString(), item.get("id").asString());
		}
		return os;
	}

	private void esperarRota(UUID celular) throws Exception {
		var limite = Instant.now().plus(Duration.ofSeconds(30));
		while (mvc.perform(get("/api/v1/campo/rota").header(CELULAR, celular).with(token("sub-agente-op")))
			.andReturn()
			.getResponse()
			.getStatus() != 200) {
			if (Instant.now().isAfter(limite)) {
				throw new AssertionError("A rota da jornada não foi publicada em 30 s.");
			}
			Thread.sleep(200);
		}
	}

	private JsonNode esperarCalculo(String id) throws Exception {
		var limite = Instant.now().plus(Duration.ofSeconds(30));
		while (true) {
			var r = ler(mvc.perform(get("/api/v1/roteirizacoes/" + id).with(token("sub-programador-op"))));
			if (!"CALCULANDO".equals(r.get("situacao").asString())) {
				assertThat(r.get("situacao").asString()).isEqualTo("PRONTA");
				return r;
			}
			if (Instant.now().isAfter(limite)) {
				throw new AssertionError("A reotimização não terminou em 30 s.");
			}
			Thread.sleep(200);
		}
	}

	/** Jornada o dia inteiro (o teste roda a qualquer hora), base a oeste. */
	private String equipe() throws Exception {
		var opcoes = ler(mvc.perform(get("/api/v1/equipes/opcoes").with(token("sub-admin-op"))));
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
						{"unidadeId":"%s","nome":"Equipe do dia","tipo":"PROPRIA","ativa":true,"membros":["%s"],
						 "operacao":{"jornadaInicio":"00:00","jornadaFim":"23:59","pausaMin":0,"tipoVeiculoId":"%s",
						 "habilidades":[%s],"base":{"latitude":-22.90,"longitude":-47.07}}}""".formatted(t.cidadeA(),
						agente, vacuo, habilidades),
				"sub-admin-op")
			.andExpect(status().isCreated())).get("id").asString();
	}

	private static JsonNode encontrar(JsonNode lista, String campo, String valor) {
		for (var item : lista) {
			if (valor.equals(item.get(campo).asString())) {
				return item;
			}
		}
		throw new AssertionError("Não achei " + campo + " = " + valor + " em " + lista);
	}

	private static Duration min(long m) {
		return Duration.ofMinutes(m);
	}

	private static String evento(String os, int sequencia, String tipo, String extra, Instant ocorrido) {
		return """
				{"id":"%s","osId":"%s","sequencia":%d,"tipo":"%s","ocorridoEm":"%s"%s}""".formatted(UUID.randomUUID(),
				os, sequencia, tipo, ocorrido, extra);
	}

	private ResultActions eventos(UUID celular, Instant enviadoEm, String... eventos) throws Exception {
		return campo("/api/v1/campo/eventos",
				"{\"enviadoEm\":\"%s\",\"eventos\":[%s]}".formatted(enviadoEm, String.join(",", eventos)), celular)
			.andExpect(status().isOk());
	}

	private static String servico(JsonNode opcoes, String codigo) {
		for (var s : opcoes.get("servicos")) {
			if (s.get("codigo").asString().equals(codigo)) {
				return s.get("id").asString();
			}
		}
		throw new AssertionError("Serviço não encontrado: " + codigo);
	}

	private static String motivo(JsonNode motivos, String tipo, String codigo) {
		for (var m : motivos) {
			if (m.get("tipo").asString().equals(tipo) && m.get("codigo").asString().equals(codigo)) {
				return m.get("id").asString();
			}
		}
		throw new AssertionError("Motivo não encontrado: " + tipo + "/" + codigo);
	}

	private ResultActions campo(String url, String corpo, UUID celular) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.header(CELULAR, celular)
			.with(token("sub-agente-op")));
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		var pedido = post(url).with(token(sub));
		if (!corpo.isEmpty()) {
			pedido = pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
		}
		return mvc.perform(pedido);
	}

	private JsonNode ler(ResultActions resposta) throws Exception {
		return json.readTree(resposta.andReturn().getResponse().getContentAsString());
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
