package dev.aurora.campanhas.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0045 (J8–J11, J13; RN-22, RN-26, RN-27): celular cadastrado, jornada e pacote do
 * dia, eventos aplicados uma vez e na ordem da sequência, parâmetros obrigatórios no
 * encerramento, fotos em partes, conflitos com a programação ("vale o campo"), posições e
 * celular revogado.
 */
// A rota calculada no início da jornada (ADR 0048) mudaria o pacote no meio das
// verificações; ela tem
// o próprio teste (RotaNaJornadaApiTest).
@SpringBootTest(properties = "aurora.roteirizacao.na-jornada=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CampoApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final String CELULAR = "X-Aurora-Dispositivo";

	private static Tenant t;

	private static UUID campo;

	private static UUID outro;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Execução de campo");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-campo", "admin@campo.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "dados_pessoais:ler",
				"dispositivo:gerenciar");
		DadosDeTeste.criarUsuario(t, "sub-programador-campo", "programador@campo.teste", t.concessao(), gestor);
		var equipeDeCampo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar", "dados_pessoais:ler");
		campo = DadosDeTeste.criarUsuario(t, "sub-agente", "agente@campo.teste", t.cidadeA(), equipeDeCampo);
		outro = DadosDeTeste.criarUsuario(t, "sub-agente-b", "agenteb@campo.teste", t.cidadeA(), equipeDeCampo);
		// Localização aproximada (pelo logradouro): o GPS da execução passa a valer.
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("""
					INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
					VALUES ('%1$s', '2001002', '%2$s', ST_SetSRID(ST_MakePoint(-47.06, -22.90), 4674), 'GEOCODIFICADA',
					        'LOGRADOURO'),
					       ('%1$s', '2001003', '%2$s', ST_SetSRID(ST_MakePoint(-47.06, -22.90), 4674), 'GEOCODIFICADA',
					        'ENDERECO');
					""".formatted(t.id(), t.setorA1()));
		}
	}

	@Test
	void doCelularAoEncerramento() throws Exception {
		var hoje = LocalDate.now(FUSO);
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-programador-campo"))));
		var vistoria = servico(opcoes, "VISTORIA");
		var motivos = opcoes.get("motivos");
		var chuva = motivo(motivos, "PARALISACAO", "CHUVA");
		var duplicada = motivo(motivos, "CANCELAMENTO", "DUPLICADA");
		var semAcesso = motivo(motivos, "DEVOLUCAO", "ENDERECO_NAO_ENCONTRADO");
		var equipeA = equipe("Equipe Campo A", campo);
		var equipeB = equipe("Equipe Campo B", outro);
		var criadas = ler(postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002","2001003","2001004"],"prioridade":"ALTA"}"""
			.formatted(vistoria), "sub-programador-campo").andExpect(status().isCreated()));
		assertThat(criadas.get("criadas").asInt()).isEqualTo(3);
		var lista = ler(mvc.perform(get("/api/v1/ordens?servico=" + vistoria).with(token("sub-programador-campo"))));
		var os1 = idDa(lista, "2001002");
		var os2 = idDa(lista, "2001003");
		var os3 = idDa(lista, "2001004");
		postar("/api/v1/ordens/programar", """
				{"ordens":["%s","%s","%s"],"equipeId":"%s","data":"%s"}""".formatted(os1, os2, os3, equipeA, hoje),
				"sub-programador-campo")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(3));

		// J13: sem celular cadastrado, nada.
		var celular = UUID.randomUUID();
		mvc.perform(get("/api/v1/campo/jornada").with(token("sub-agente")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("DISPOSITIVO_NAO_CADASTRADO"));
		mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-programador-campo")))
			.andExpect(status().isForbidden());
		campo("/api/v1/campo/dispositivo", """
				{"modelo":"Moto G","sistema":"Android 15","versaoApp":"1.0.0"}""", celular, "sub-agente")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revogado").value(false));

		// J8: equipes da pessoa com o resumo de hoje, antes de iniciar.
		mvc.perform(get("/api/v1/campo/equipes").header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].ordensHoje").value(3))
			.andExpect(jsonPath("$[0].emergenciais").value(0))
			.andExpect(jsonPath("$[0].veiculoNome").value("Caminhão de vácuo"))
			.andExpect(jsonPath("$[0].jornadaAberta").value(false));

		// J8: pacote do dia com os parâmetros da versão; abrir a jornada.
		var pacote = ler(mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ids.length()").value(3))
			.andExpect(jsonPath("$.jornada").doesNotExist())
			.andExpect(jsonPath("$.raioInicioM").value(300))
			.andExpect(jsonPath("$.ordens[0].ultimaSequencia").value(0))
			// ADR 0053: o catálogo semeado pede foto em todas as fases.
			.andExpect(jsonPath("$.ordens[0].fasesComFoto.length()").value(4)));
		var versao = pacote.get("ordens").get(0).get("servicoVersaoId").asString();
		assertThat(pacote.get("parametros").get(versao).size()).isEqualTo(4);
		var versaoDoPacote = pacote.get("versao").asLong();
		var inicioDaJornada = Instant.now().minusSeconds(3600);
		campo("/api/v1/campo/jornada/inicio",
				"""
						{"id":"%s","ocorridoEm":"%s","posicao":{"latitude":-22.91,"longitude":-47.07,"precisaoM":8}}"""
					.formatted(UUID.randomUUID(), inicioDaJornada),
				celular, "sub-agente")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jornada.equipeNome").value("Equipe Campo A"));
		// Sem mudança, a diferença vem vazia.
		mvc.perform(
				get("/api/v1/campo/jornada?desde=" + versaoDoPacote).header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(jsonPath("$.diferenca").value(true))
			.andExpect(jsonPath("$.ordens.length()").value(0))
			.andExpect(jsonPath("$.ids.length()").value(3));

		// RN-26: a paralisação (2) chega antes do início (1) e espera; o início longe do
		// alvo fica marcado; o reenvio do início não aplica de novo.
		var iniciar = UUID.randomUUID();
		var paralisar = UUID.randomUUID();
		var fotoDoInicio = UUID.randomUUID();
		var agora = Instant.now();
		eventos(celular, agora,
				evento(paralisar, os1, 2, "PARALISAR", ",\"motivoId\":\"" + chuva + "\"", agora.minusSeconds(1700)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("AGUARDANDO"));
		eventos(celular, agora,
				evento(iniciar, os1, 1, "INICIAR",
						",\"posicao\":{\"latitude\":-22.95,\"longitude\":-47.06},\"fotos\":[\"" + fotoDoInicio + "\"]",
						agora.minusSeconds(1800)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("APLICADO"))
			.andExpect(jsonPath("$.eventos[0].avisos.length()").value(1))
			.andExpect(jsonPath("$.eventos[0].avisos[0]").value("FORA_DO_LOCAL"));
		assertThat(ordem(os1).get("status").asString()).isEqualTo("PARALISADA");
		eventos(celular, agora.minusSeconds(900), evento(iniciar, os1, 1, "INICIAR", "", agora.minusSeconds(1800)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("REPETIDO"))
			.andExpect(jsonPath("$.eventos[0].original").value("APLICADO"));
		// Paralisada não encerra; retoma primeiro. Sequência repetida é recusada.
		eventos(celular, agora, evento(UUID.randomUUID(), os1, 2, "RETOMAR", "", agora.minusSeconds(1500)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("REJEITADO"));
		eventos(celular, agora,
				evento(UUID.randomUUID(), os1, 3, "ENCERRAR", ",\"resultado\":\"EXECUTADA\"", agora.minusSeconds(1400)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("REJEITADO"))
			.andExpect(jsonPath("$.eventos[0].mensagem").value("Não é possível encerrar uma OS paralisada."));
		// ADR 0053: a mesma foto duas vezes é recusada; sem foto, vale o campo com aviso.
		var repetida = UUID.randomUUID();
		eventos(celular, agora,
				evento(UUID.randomUUID(), os1, 4, "RETOMAR", ",\"fotos\":[\"%s\",\"%s\"]".formatted(repetida, repetida),
						agora.minusSeconds(1350)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("REJEITADO"))
			.andExpect(jsonPath("$.eventos[0].mensagem").value("A mesma foto aparece duas vezes na ação."));
		eventos(celular, agora, evento(UUID.randomUUID(), os1, 5, "RETOMAR", "", agora.minusSeconds(1300)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("APLICADO"))
			.andExpect(jsonPath("$.eventos[0].avisos[0]").value("SEM_FOTO"));
		// O pacote diz até onde a sequência da OS já foi (a recusada também ocupa o
		// número).
		mvc.perform(
				get("/api/v1/campo/jornada?desde=" + versaoDoPacote).header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(jsonPath("$.ordens[?(@.ordem.id == '%s')].ultimaSequencia".formatted(os1)).value(5));

		// RN-22: encerrar executada exige os parâmetros obrigatórios e as fotos mínimas.
		var foto = UUID.randomUUID();
		eventos(celular, agora, evento(UUID.randomUUID(), os1, 6, "ENCERRAR", """
				,"resultado":"EXECUTADA","valores":{"viavel":true,"abaixo_da_rede":false}""", agora.minusSeconds(1200)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("REJEITADO"))
			.andExpect(jsonPath("$.eventos[0].mensagem").value("Preencha \"Foto da fachada\"."));
		eventos(celular, agora, evento(UUID.randomUUID(), os1, 7, "ENCERRAR", """
				,"resultado":"EXECUTADA","valores":{"viavel":true,"abaixo_da_rede":false,"distancia_rede":12.5,
				 "foto":["%s"]},"posicao":{"latitude":-22.9001,"longitude":-47.0601,"precisaoM":5}""".formatted(foto),
				agora.minusSeconds(1100)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("APLICADO"));
		var encerrada = ordem(os1);
		assertThat(encerrada.get("status").asString()).isEqualTo("ENCERRADA");
		assertThat(encerrada.get("resultado").asString()).isEqualTo("EXECUTADA");
		assertThat(encerrada.get("latitude").asDouble()).isEqualTo(-22.9001);

		// Foto em duas partes, retomando de onde parou; a web vê a foto.
		var jpeg = new byte[3000];
		jpeg[0] = (byte) 0xFF;
		jpeg[1] = (byte) 0xD8;
		jpeg[2] = (byte) 0xFF;
		var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg));
		parte(celular, foto, os1, jpeg, 0, 1999, hash).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("RECEBENDO"))
			.andExpect(jsonPath("$.recebido").value(2000));
		parte(celular, foto, os1, jpeg, 2500, 2999, hash).andExpect(status().isConflict());
		parte(celular, foto, os1, jpeg, 2000, 2999, hash).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("DISPONIVEL"));
		parte(celular, foto, os1, jpeg, 2000, 2999, hash).andExpect(status().isOk());
		var conteudo = mvc
			.perform(get("/api/v1/ordens/{id}/anexos/{a}", os1, foto).with(token("sub-programador-campo")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(conteudo).isEqualTo(jpeg);
		// Conteúdo que não confere com o SHA-256 é recusado.
		var corrompida = UUID.randomUUID();
		parte(celular, corrompida, os1, "texto".getBytes(StandardCharsets.UTF_8), 0, 4, hash).andExpect(status().isOk())
			.andExpect(jsonPath("$.situacao").value("RECUSADO"));
		mvc.perform(get("/api/v1/ordens/{id}/execucao", os1).with(token("sub-programador-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventos.length()").value(8))
			.andExpect(jsonPath("$.eventos[0].tipo").value("INICIAR"))
			.andExpect(jsonPath("$.eventos[0].fotos[0]").value(fotoDoInicio.toString()))
			.andExpect(jsonPath("$.eventos[0].distanciaM").isNumber())
			.andExpect(jsonPath("$.anexos.length()").value(2))
			.andExpect(jsonPath("$.parametros.length()").value(4));

		// 10.9.5: a OS 2 passou para a equipe B, mas a equipe A já tinha iniciado: vale o
		// campo, com conflito para o programador.
		postar("/api/v1/ordens/programar", """
				{"ordens":["%s"],"equipeId":"%s","data":"%s"}""".formatted(os2, equipeB, hoje), "sub-programador-campo")
			.andExpect(jsonPath("$.programadas").value(1));
		eventos(celular, agora, evento(UUID.randomUUID(), os2, 1, "INICIAR", "", agora.minusSeconds(600)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("CONFLITO"))
			.andExpect(jsonPath("$.eventos[0].avisos[0]").value("SEM_GPS"));
		var os2Depois = ordem(os2);
		assertThat(os2Depois.get("status").asString()).isEqualTo("EM_EXECUCAO");
		assertThat(os2Depois.get("equipeId").asString()).isEqualTo(equipeA);
		// A OS 3 foi cancelada e o campo só devolveu: vale a programação.
		postar("/api/v1/ordens/" + os3 + "/cancelar", "{\"motivoId\":\"%s\"}".formatted(duplicada),
				"sub-programador-campo")
			.andExpect(status().isOk());
		eventos(celular, agora, evento(UUID.randomUUID(), os3, 1, "DEVOLVER", ",\"motivoId\":\"" + semAcesso + "\"",
				agora.minusSeconds(500)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("SUPERADO"));
		assertThat(ordem(os3).get("status").asString()).isEqualTo("CANCELADA");

		// J11: caixa de conflitos e decisão registrada.
		var conflitos = ler(mvc.perform(get("/api/v1/ordens/conflitos").with(token("sub-programador-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.abertos").value(1))
			.andExpect(jsonPath("$.itens[0].tipo").value("OUTRA_EQUIPE"))
			.andExpect(jsonPath("$.itens[0].acao").value("INICIAR")));
		var conflito = conflitos.get("itens").get(0).get("id").asString();
		postar("/api/v1/ordens/conflitos/" + conflito + "/decisao", "{\"decisao\":\"Fica com a equipe A.\"}",
				"sub-programador-campo")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decididoPor").isString());
		postar("/api/v1/ordens/conflitos/" + conflito + "/decisao", "{\"decisao\":\"De novo\"}",
				"sub-programador-campo")
			.andExpect(status().isConflict());

		// RN-27: posições só dentro da jornada; J8: não encerra com OS em execução.
		campo("/api/v1/campo/posicoes",
				"""
						{"posicoes":[{"momento":"%s","latitude":-22.9,"longitude":-47.06,"precisaoM":10},
						             {"momento":"%s","latitude":-22.9,"longitude":-47.06},
						             {"momento":"%s","latitude":-22.9,"longitude":-47.06}]}"""
					.formatted(agora.minusSeconds(300), agora.minusSeconds(300), inicioDaJornada.minusSeconds(60)),
				celular, "sub-agente")
			.andExpect(jsonPath("$.gravadas").value(1));
		campo("/api/v1/campo/jornada/fim", "{\"ocorridoEm\":\"%s\"}".formatted(agora), celular, "sub-agente")
			.andExpect(status().isConflict());
		eventos(celular, agora, evento(UUID.randomUUID(), os2, 2, "PARALISAR", ",\"motivoId\":\"" + chuva + "\"",
				agora.minusSeconds(200)))
			.andExpect(jsonPath("$.eventos[0].situacao").value("APLICADO"));
		campo("/api/v1/campo/jornada/fim", "{\"ocorridoEm\":\"%s\"}".formatted(agora), celular, "sub-agente")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fim").isString());

		// J13: celular bloqueado perde o acesso, mas ainda envia a fila pendente.
		mvc.perform(get("/api/v1/dispositivos").with(token("sub-programador-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].usuarioNome").isString());
		mvc.perform(post("/api/v1/dispositivos/{id}/revogar", celular).with(token("sub-programador-campo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revogado").value(true));
		mvc.perform(get("/api/v1/campo/jornada").header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("DISPOSITIVO_REVOGADO"));
		eventos(celular, agora, evento(UUID.randomUUID(), os2, 3, "RETOMAR", "", agora.minusSeconds(100)))
			.andExpect(jsonPath("$.revogado").value(true))
			.andExpect(jsonPath("$.eventos[0].situacao").value("APLICADO"))
			.andExpect(jsonPath("$.eventos[0].avisos[0]").value("APARELHO_REVOGADO"));
		mvc.perform(post("/api/v1/campo/dispositivo/apagado").header(CELULAR, celular).with(token("sub-agente")))
			.andExpect(status().isOk());
		campo("/api/v1/campo/dispositivo", """
				{"modelo":"Moto G","sistema":"Android 15","versaoApp":"1.0.1"}""", celular, "sub-agente")
			.andExpect(status().isForbidden());
	}

	private String equipe(String nome, UUID membro) throws Exception {
		return ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"%s","tipo":"PROPRIA","ativa":true,"membros":["%s"],%s}"""
					.formatted(t.cidadeA(), nome, membro, operacaoCompleta("sub-admin-campo")),
				"sub-admin-campo")
			.andExpect(status().isCreated())).get("id").asString();
	}

	private static String evento(UUID id, String os, int sequencia, String tipo, String extra, Instant ocorrido) {
		return """
				{"id":"%s","osId":"%s","sequencia":%d,"tipo":"%s","ocorridoEm":"%s"%s}""".formatted(id, os, sequencia,
				tipo, ocorrido, extra);
	}

	private ResultActions eventos(UUID celular, Instant enviadoEm, String... eventos) throws Exception {
		return campo("/api/v1/campo/eventos",
				"{\"enviadoEm\":\"%s\",\"eventos\":[%s]}".formatted(enviadoEm, String.join(",", eventos)), celular,
				"sub-agente")
			.andExpect(status().isOk());
	}

	private ResultActions parte(UUID celular, UUID id, String os, byte[] arquivo, int inicio, int fim, String hash)
			throws Exception {
		var pedaco = java.util.Arrays.copyOfRange(arquivo, inicio, fim + 1);
		return mvc
			.perform(put("/api/v1/campo/anexos/{id}?os={os}&parametro=foto", id, os).contentType(MediaType.IMAGE_JPEG)
				.header("Content-Range", "bytes %d-%d/%d".formatted(inicio, fim, arquivo.length))
				.header("X-Conteudo-Sha256", hash)
				.header(CELULAR, celular)
				.content(pedaco)
				.with(token("sub-agente")));
	}

	private JsonNode ordem(String id) throws Exception {
		return ler(mvc.perform(get("/api/v1/ordens/{id}", id).with(token("sub-programador-campo")))
			.andExpect(status().isOk()));
	}

	private ResultActions campo(String url, String corpo, UUID celular, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(corpo)
			.header(CELULAR, celular)
			.with(token(sub)));
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(sub)));
	}

	private JsonNode ler(ResultActions resposta) throws Exception {
		return json.readTree(resposta.andReturn().getResponse().getContentAsString());
	}

	private static String idDa(JsonNode pagina, String matricula) {
		for (var item : pagina.get("itens")) {
			if (matricula.equals(item.get("matricula").asString())) {
				return item.get("id").asString();
			}
		}
		throw new AssertionError("OS não encontrada: " + matricula);
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

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

	/**
	 * Operação de uma equipe que atende todo o catálogo de teste (RN-23): todas as
	 * habilidades e caminhão de vácuo (atende moto, carro e o próprio vácuo).
	 */
	private String operacaoCompleta(String sub) throws Exception {
		var opcoes = json.readTree(mvc.perform(get("/api/v1/equipes/opcoes").with(token(sub)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		String vacuo = null;
		for (var v : opcoes.get("veiculos")) {
			if ("CAMINHAO_VACUO".equals(v.get("codigo").asString())) {
				vacuo = v.get("id").asString();
			}
		}
		var habilidades = new java.util.StringJoiner(",");
		opcoes.get("habilidades").forEach(h -> habilidades.add("\"" + h.get("id").asString() + "\""));
		return """
				"operacao":{"jornadaInicio":"07:30","jornadaFim":"16:30","pausaMin":60,"tipoVeiculoId":"%s","habilidades":[%s]}"""
			.formatted(vacuo, habilidades);
	}

}
