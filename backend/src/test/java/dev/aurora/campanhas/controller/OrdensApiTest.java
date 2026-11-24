package dev.aurora.campanhas.controller;

import java.time.LocalDate;
import java.time.ZoneId;
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
 * ADR 0044 (J4, J5): OS avulsa por imóvel, OS de trecho, lote por logradouro na ordem da
 * numeração, programação para a equipe (RN-23), devolução, reprogramação, cancelamento e
 * encerramento pelo gestor; matrícula e número só com {@code dados_pessoais:ler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrdensApiTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static Tenant t;

	private static UUID campo;

	private static final UUID LOGRADOURO = UUID.randomUUID();

	private static final UUID TRECHO = UUID.randomUUID();

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Programação de OS");
		DadosDeTeste.criarHistoricoDeImoveis(t);
		DadosDeTeste.semearCatalogo(t);
		DadosDeTeste.criarUsuario(t, "sub-admin-os", "admin@os.teste", t.grupo());
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "os:programar", "dados_pessoais:ler");
		DadosDeTeste.criarUsuario(t, "sub-programador", "programador@os.teste", t.concessao(), gestor);
		var semDados = DadosDeTeste.criarPerfil(t, "Programador sem dados", "os:programar");
		DadosDeTeste.criarUsuario(t, "sub-sem-dados", "semdados@os.teste", t.cidadeA(), semDados);
		var equipeDeCampo = DadosDeTeste.criarPerfil(t, "Campo", "os:executar");
		campo = DadosDeTeste.criarUsuario(t, "sub-campo-os", "campo@os.teste", t.cidadeA(), equipeDeCampo);
		// Rua das Flores (Cidade A): um trecho de 120,5 m; 10, 11 e 12 com número e
		// 2001005
		// sem número.
		try (var c = BancoDeTeste.comoSuperusuario(); var s = c.createStatement()) {
			s.execute("""
					INSERT INTO logradouro (id, tenant_id, unidade_id, codigo, tipo, nome, nome_busca)
					VALUES ('%1$s', '%2$s', '%3$s', 'L-1', 'Rua', 'Rua das Flores', 'rua das flores');
					INSERT INTO trecho (id, tenant_id, unidade_id, logradouro_id, codigo, geom, extensao_m, lado)
					VALUES ('%4$s', '%2$s', '%3$s', '%1$s', 'T-1',
					        ST_GeomFromText('LINESTRING(-47.06 -22.90, -47.05 -22.90)', 4674), 120.5, 'AMBOS');
					INSERT INTO imovel_endereco (tenant_id, codigo_origem, unidade_id, logradouro_id, trecho_id, numero,
					                             numero_inteiro)
					VALUES ('%2$s', '2001002', '%5$s', '%1$s', '%4$s', '10', 10),
					       ('%2$s', '2001003', '%5$s', '%1$s', '%4$s', '11', 11),
					       ('%2$s', '2001004', '%5$s', '%1$s', '%4$s', '12A', 12),
					       ('%2$s', '2001005', '%5$s', '%1$s', NULL, 'S/N', NULL);
					""".formatted(LOGRADOURO, t.id(), t.cidadeA(), TRECHO, t.setorA1()));
		}
	}

	@Test
	void daCriacaoAoEncerramento() throws Exception {
		var hoje = LocalDate.now(FUSO);
		mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-campo-os"))).andExpect(status().isForbidden());
		var opcoes = ler(mvc.perform(get("/api/v1/ordens/opcoes").with(token("sub-programador")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servicos.length()").value(12)));
		var vistoria = servico(opcoes, "VISTORIA");
		var limpeza = servico(opcoes, "LIMPEZA_REDE");

		// J4: avulsa por imóvel; duplicada e matrícula de fora são informadas.
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002"],"prioridade":"ALTA"}""".formatted(limpeza),
				"sub-programador")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002"," 2001002 ","X-999"],"prioridade":"ALTA",
				 "instrucoes":"Portão azul"}""".formatted(vistoria), "sub-programador").andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(1))
			.andExpect(jsonPath("$.totalIgnoradas").value(1))
			.andExpect(jsonPath("$.ignoradas[0].alvo").value("X-999"));
		postar("/api/v1/ordens/imoveis", """
				{"servicoId":"%s","matriculas":["2001002"],"prioridade":"MEDIA"}""".formatted(vistoria),
				"sub-programador")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(0))
			.andExpect(jsonPath("$.ignoradas[0].motivo").value("Já tem OS aberta deste serviço"));

		// J5: OS de trecho com a extensão como quantidade (serviço por metro).
		postar("/api/v1/ordens/trechos", """
				{"servicoId":"%s","trechos":["%s"],"prioridade":"MEDIA"}""".formatted(vistoria, TRECHO),
				"sub-programador")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/ordens/trechos", """
				{"servicoId":"%s","trechos":["%s"],"prioridade":"MEDIA"}""".formatted(limpeza, TRECHO),
				"sub-programador")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(1));

		// J5: prévia e lote por logradouro (2001002 já tem vistoria aberta).
		var faixa = """
				{"logradouroId":"%s","numeroInicial":%s,"numeroFinal":%s,"lado":"%s","servicoId":"%s"}""";
		postar("/api/v1/ordens/lotes/previa", faixa.formatted(LOGRADOURO, null, null, "AMBOS", vistoria),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logradouroNome").value("Rua das Flores"))
			.andExpect(jsonPath("$.imoveis").value(4))
			.andExpect(jsonPath("$.comOsAberta").value(1))
			.andExpect(jsonPath("$.novas").value(3));
		postar("/api/v1/ordens/lotes/previa", faixa.formatted(LOGRADOURO, 10, 12, "PAR", vistoria), "sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imoveis").value(2))
			.andExpect(jsonPath("$.novas").value(1));
		postar("/api/v1/ordens/lotes/previa", faixa.formatted(LOGRADOURO, 12, 10, "AMBOS", vistoria), "sub-programador")
			.andExpect(status().isUnprocessableContent());
		var lote = ler(postar("/api/v1/ordens/lotes",
				"""
						{"faixa":%s,"prioridade":"BAIXA"}"""
					.formatted(faixa.formatted(LOGRADOURO, null, null, "AMBOS", vistoria)),
				"sub-programador")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(3))
			.andExpect(jsonPath("$.totalIgnoradas").value(1))).get("loteId").asString();

		// Lote na ordem da numeração: pares (12A), ímpares (11), sem número.
		var doLote = lista("?lote=" + lote, "sub-programador");
		assertThat(doLote.get("total").asInt()).isEqualTo(3);
		var numeros = new long[3];
		var matriculas = new String[3];
		for (int i = 0; i < 3; i++) {
			numeros[i] = doLote.get("itens").get(i).get("numero").asLong();
			matriculas[i] = doLote.get("itens").get(i).get("matricula").asString();
		}
		assertThat(matriculas).containsExactly("2001004", "2001003", "2001005");
		assertThat(numeros[1]).isEqualTo(numeros[0] + 1);
		assertThat(doLote.get("itens").get(0).get("origem").asString()).isEqualTo("LOTE");
		assertThat(doLote.get("itens").get(0).get("numeroImovel").asString()).isEqualTo("12A");

		var todas = lista("", "sub-programador");
		assertThat(todas.get("total").asInt()).isEqualTo(5);
		var avulsa = item(todas, "VISTORIA", "2001002");
		assertThat(avulsa.get("origem").asString()).isEqualTo("AVULSA");
		assertThat(avulsa.get("instrucoes").asString()).isEqualTo("Portão azul");
		assertThat(avulsa.get("atrasada").asBoolean()).isFalse();
		var deTrecho = item(todas, "LIMPEZA_REDE", null);
		assertThat(deTrecho.get("alvo").asString()).isEqualTo("TRECHO");
		assertThat(deTrecho.get("trechoCodigo").asString()).isEqualTo("T-1");
		assertThat(deTrecho.get("quantidadePrevista").decimalValue()).isEqualByComparingTo("120.5");
		assertThat(deTrecho.get("latitude").asDouble()).isEqualTo(-22.90);
		// Sem dados_pessoais:ler: matrícula mascarada, sem número.
		var semDados = item(lista("?busca=" + avulsa.get("numero").asLong(), "sub-sem-dados"), "VISTORIA", null);
		assertThat(semDados.get("matricula").asString()).isEqualTo("••••002");
		assertThat(semDados.get("numeroImovel").isNull()).isTrue();

		// Programar (RN-23): equipe da Cidade A recebe as três; data no passado não.
		var equipe = ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"Equipe Rede A","tipo":"PROPRIA","ativa":true,"membros":["%s"],%s}"""
					.formatted(t.cidadeA(), campo, operacaoCompleta("sub-admin-os")),
				"sub-admin-os")
			.andExpect(status().isCreated())).get("id").asString();
		mvc.perform(get("/api/v1/ordens/equipes").with(token("sub-programador")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].nome").value("Equipe Rede A"));
		var idAvulsa = avulsa.get("id").asString();
		var idTrecho = deTrecho.get("id").asString();
		var idLote1 = doLote.get("itens").get(0).get("id").asString();
		var idLote2 = doLote.get("itens").get(1).get("id").asString();
		var programar = """
				{"ordens":["%s","%s","%s"],"equipeId":"%s","data":"%s"}""";
		postar("/api/v1/ordens/programar", programar.formatted(idAvulsa, idTrecho, idLote1, equipe, hoje.minusDays(1)),
				"sub-programador")
			.andExpect(status().isUnprocessableContent());
		// Jornada invertida e veículo que não existe são recusados.
		postar("/api/v1/equipes", """
				{"unidadeId":"%s","nome":"Equipe torta","tipo":"PROPRIA","ativa":true,"membros":[],
				 "operacao":{"jornadaInicio":"17:00","jornadaFim":"08:00","pausaMin":60,"habilidades":[]}}"""
			.formatted(t.cidadeA()), "sub-admin-os").andExpect(status().isUnprocessableContent());
		postar("/api/v1/equipes", """
				{"unidadeId":"%s","nome":"Equipe torta","tipo":"PROPRIA","ativa":true,"membros":[],
				 "operacao":{"jornadaInicio":"08:00","jornadaFim":"17:00","pausaMin":60,"tipoVeiculoId":"%s",
				 "habilidades":[]}}""".formatted(t.cidadeA(), java.util.UUID.randomUUID()), "sub-admin-os")
			.andExpect(status().isUnprocessableContent());
		// RN-23: equipe sem veículo nem habilidades não recebe nenhuma (vistoria exige
		// moto; limpeza, vácuo).
		var incompleta = ler(postar("/api/v1/equipes",
				"""
						{"unidadeId":"%s","nome":"Equipe a pé","tipo":"PROPRIA","ativa":true,"membros":[]}"""
					.formatted(t.cidadeA()),
				"sub-admin-os")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.operacao.jornadaInicio").value("07:30:00"))
			.andExpect(jsonPath("$.operacao.habilidades.length()").value(0))).get("id").asString();
		postar("/api/v1/ordens/programar", programar.formatted(idAvulsa, idTrecho, idLote1, incompleta, hoje),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(0))
			.andExpect(jsonPath("$.incompativeis").value(3));
		postar("/api/v1/ordens/programar", programar.formatted(idAvulsa, idTrecho, idLote1, equipe, hoje),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(3))
			.andExpect(jsonPath("$.foraDaArea").value(0))
			.andExpect(jsonPath("$.incompativeis").value(0));
		// Reprogramar para amanhã.
		postar("/api/v1/ordens/programar", programar.formatted(idLote1, idLote1, idLote1, equipe, hoje.plusDays(1)),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(1));
		mvc.perform(get("/api/v1/minhas-ordens").with(token("sub-campo-os")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[2].dataProgramada").value(hoje.plusDays(1).toString()));

		// Cancelar: só com motivo de cancelamento e antes de a equipe começar.
		var motivos = opcoes.get("motivos");
		var duplicada = motivo(motivos, "CANCELAMENTO", "DUPLICADA");
		var enderecoNaoEncontrado = motivo(motivos, "DEVOLUCAO", "ENDERECO_NAO_ENCONTRADO");
		postar("/api/v1/ordens/" + idLote2 + "/cancelar", "{\"motivoId\":\"%s\"}".formatted(enderecoNaoEncontrado),
				"sub-programador")
			.andExpect(status().isUnprocessableContent());
		postar("/api/v1/ordens/" + idLote2 + "/cancelar", "{\"motivoId\":\"%s\"}".formatted(duplicada),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELADA"))
			.andExpect(jsonPath("$.motivoNome").value("OS duplicada"));
		// A cancelada some da lista de abertas e continua na lista completa.
		assertThat(lista("?abertas=true&tamanho=200", "sub-programador").toString()).doesNotContain(idLote2.toString());
		assertThat(lista("?tamanho=200", "sub-programador").toString()).contains(idLote2.toString());
		postar("/api/v1/ordens/" + idLote2 + "/cancelar", "{\"motivoId\":\"%s\"}".formatted(duplicada),
				"sub-programador")
			.andExpect(status().isConflict());

		// A equipe devolve a de trecho; o programador reprograma.
		postar("/api/v1/minhas-ordens/" + idTrecho + "/devolver",
				"{\"motivoId\":\"%s\",\"observacao\":\"Rua interditada\"}".formatted(enderecoNaoEncontrado),
				"sub-campo-os")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DEVOLVIDA"));
		assertThat(lista("?status=DEVOLVIDA", "sub-programador").get("total").asInt()).isEqualTo(1);
		postar("/api/v1/ordens/programar", programar.formatted(idTrecho, idTrecho, idTrecho, equipe, hoje),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(1));

		// O gestor encerra pela empreiteira; encerrada não volta nem é cancelada.
		mvc.perform(put("/api/v1/ordens/{id}/resultado", idTrecho).contentType(MediaType.APPLICATION_JSON)
			.content("{\"resultado\":\"EXECUTADA\",\"quantidadeExecutada\":80}")
			.with(token("sub-programador")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENCERRADA"))
			.andExpect(jsonPath("$.quantidadeExecutada").value(80))
			.andExpect(jsonPath("$.conferencia").value("NAO_SE_APLICA"));
		postar("/api/v1/ordens/" + idTrecho + "/cancelar", "{\"motivoId\":\"%s\"}".formatted(duplicada),
				"sub-programador")
			.andExpect(status().isConflict());
		postar("/api/v1/ordens/programar", programar.formatted(idTrecho, idTrecho, idTrecho, equipe, hoje),
				"sub-programador")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.programadas").value(0))
			.andExpect(jsonPath("$.ignoradas").value(1));
		// Com a OS encerrada, uma nova do mesmo serviço pode ser criada no trecho.
		postar("/api/v1/ordens/trechos", """
				{"servicoId":"%s","trechos":["%s"],"prioridade":"EMERGENCIAL"}""".formatted(limpeza, TRECHO),
				"sub-programador")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criadas").value(1));
		mvc.perform(get("/api/v1/ordens/{id}", idTrecho).with(token("sub-programador")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logradouroNome").value("Rua das Flores"));
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(sub)));
	}

	private JsonNode ler(ResultActions resposta) throws Exception {
		return json.readTree(resposta.andReturn().getResponse().getContentAsString());
	}

	private JsonNode lista(String filtro, String sub) throws Exception {
		return ler(mvc.perform(get("/api/v1/ordens" + filtro).with(token(sub))).andExpect(status().isOk()));
	}

	private static JsonNode item(JsonNode pagina, String servico, String matricula) {
		for (var item : pagina.get("itens")) {
			if (item.get("servicoCodigo").asString().equals(servico)
					&& (matricula == null || matricula.equals(item.get("matricula").asString()))) {
				return item;
			}
		}
		throw new AssertionError("OS não encontrada: " + servico + " " + matricula);
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
