package dev.aurora.servicos.controller;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0042 (J1, J2, RN-20): catálogo inicial, cadastro de serviço com parâmetros, nova
 * versão só quando a definição muda, itens de apoio, permissão e isolamento entre
 * clientes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogoApiTest {

	private static Tenant t;

	private static Tenant outro;

	/** Só leitura: os outros testes alteram o catálogo de {@link #t}. */
	private static Tenant semente;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Catálogo de serviços");
		outro = DadosDeTeste.criarTenant("Catálogo de outro cliente");
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor de serviços", "servico:gerenciar");
		DadosDeTeste.criarUsuario(t, "sub-gestor-servicos", "gestor@catalogo.teste", t.grupo(), gestor);
		var leitor = DadosDeTeste.criarPerfil(t, "Leitor", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-leitor-catalogo", "leitor@catalogo.teste", t.grupo(), leitor);
		var gestorOutro = DadosDeTeste.criarPerfil(outro, "Gestor de serviços", "servico:gerenciar");
		DadosDeTeste.criarUsuario(outro, "sub-gestor-outro", "gestor@outro.teste", outro.grupo(), gestorOutro);
		semente = DadosDeTeste.criarTenant("Catálogo inicial");
		var gestorSemente = DadosDeTeste.criarPerfil(semente, "Gestor de serviços", "servico:gerenciar");
		DadosDeTeste.criarUsuario(semente, "sub-gestor-semente", "gestor@semente.teste", semente.grupo(),
				gestorSemente);
		try (var c = BancoDeTeste.comoSuperusuario(); var comando = c.createStatement()) {
			comando.execute("SELECT semear_catalogo_servicos('%s')".formatted(t.id()));
			comando.execute("SELECT semear_catalogo_servicos('%s')".formatted(semente.id()));
			// Idempotente: chamar de novo não duplica.
			comando.execute("SELECT semear_catalogo_servicos('%s')".formatted(semente.id()));
		}
	}

	@Test
	void catalogoInicialSemeadoESoDoCliente() throws Exception {
		mvc.perform(get("/api/v1/catalogo").with(token(semente, "sub-gestor-semente")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grupos", hasSize(4)))
			.andExpect(jsonPath("$.servicos", hasSize(12)))
			.andExpect(jsonPath("$.veiculos", hasSize(5)))
			.andExpect(jsonPath("$.habilidades", hasSize(4)))
			.andExpect(jsonPath("$.motivos", hasSize(26)))
			.andExpect(jsonPath("$.servicos[?(@.codigo == 'LIGACAO_NOVA')].definicao.conferidoPelaCarga").value(true))
			.andExpect(jsonPath("$.servicos[?(@.codigo == 'LIGACAO_NOVA')].definicao.parametros[0].chave")
				.value("extensao_ramal"))
			.andExpect(
					jsonPath("$.servicos[?(@.codigo == 'LANCAMENTO_IRREGULAR')].definicao.parametros[2].condicao.chave")
						.value("teste_corante"))
			.andExpect(
					jsonPath("$.motivos[?(@.tipo == 'DEVOLUCAO' && @.codigo == 'OUTRO')].exigeObservacao").value(true));

		// O outro cliente não foi semeado aqui e não vê o catálogo deste.
		mvc.perform(get("/api/v1/catalogo").with(token(outro, "sub-gestor-outro")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servicos", hasSize(0)));
		mvc.perform(get("/api/v1/catalogo").with(token(t, "sub-leitor-catalogo"))).andExpect(status().isForbidden());
	}

	@Test
	void servicoVersionadoSoQuandoADefinicaoMuda() throws Exception {
		var catalogo = json.readTree(mvc.perform(get("/api/v1/catalogo").with(token(t, "sub-gestor-servicos")))
			.andReturn()
			.getResponse()
			.getContentAsString());
		var grupo = catalogo.get("grupos").get(0).get("id").asString();
		var corpo = """
				{"grupoId":"%s","ativo":true,"definicao":{"nome":"Troca de tampa de PV","unidadeMedida":"UN",
				 "alvo":"TRECHO","tempoPadraoMin":45.0,"tempoPorUnidade":false,
				 "prazos":{"emergencialHoras":12,"altaDias":2,"mediaDias":5,"baixaDias":10},
				 "conferidoPelaCarga":false,"parametros":[
				   {"chave":"trocada","rotulo":"Tampa trocada","tipo":"SIM_NAO","obrigatorio":true},
				   {"chave":"motivo","rotulo":"Por que não","tipo":"TEXTO","obrigatorio":true,
				    "condicao":{"chave":"trocada","valor":"false"}},
				   {"chave":"fotos","rotulo":"Fotos","tipo":"FOTO","obrigatorio":true,"minimo":1,"maximo":%d}]}}""";
		var criado = json.readTree(
				enviar(post("/api/v1/catalogo/servicos"), corpo.formatted(grupo, 3)).andExpect(status().isCreated())
					.andExpect(jsonPath("$.codigo").value("TROCA_DE_TAMPA_DE_PV"))
					.andExpect(jsonPath("$.versao").value(1))
					.andExpect(jsonPath("$.definicao.tempoPadraoMin").value(45))
					.andExpect(jsonPath("$.definicao.parametros[1].condicao.valor").value("false"))
					.andExpect(jsonPath("$.definicao.parametros[2].maximo").value(3))
					.andReturn()
					.getResponse()
					.getContentAsString());
		var id = criado.get("id").asString();

		// Mesma definição (só a situação muda): continua na versão 1.
		enviar(put("/api/v1/catalogo/servicos/{id}", id),
				corpo.formatted(grupo, 3).replace("\"ativo\":true", "\"ativo\":false"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.versao").value(1))
			.andExpect(jsonPath("$.ativo").value(false));
		// Parâmetro diferente: versão 2.
		enviar(put("/api/v1/catalogo/servicos/{id}", id), corpo.formatted(grupo, 5)).andExpect(status().isOk())
			.andExpect(jsonPath("$.versao").value(2));
		mvc.perform(get("/api/v1/catalogo/servicos/{id}/versoes", id).with(token(t, "sub-gestor-servicos")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].versao").value(2))
			.andExpect(jsonPath("$[0].definicao.parametros[2].maximo").value(5))
			.andExpect(jsonPath("$[1].definicao.parametros[2].maximo").value(3))
			.andExpect(jsonPath("$[0].criadaPor").isNotEmpty());

		// Código repetido, condição inválida e serviço de trecho conferido pela carga.
		enviar(post("/api/v1/catalogo/servicos"), corpo.formatted(grupo, 3)).andExpect(status().isConflict());
		enviar(post("/api/v1/catalogo/servicos"),
				corpo.formatted(grupo, 3)
					.replace("Troca de tampa", "Outra troca")
					.replace("\"valor\":\"false\"", "\"valor\":\"talvez\""))
			.andExpect(status().isUnprocessableContent());
		enviar(post("/api/v1/catalogo/servicos"),
				corpo.formatted(grupo, 3)
					.replace("Troca de tampa", "Mais uma troca")
					.replace("\"conferidoPelaCarga\":false", "\"conferidoPelaCarga\":true"))
			.andExpect(status().isUnprocessableContent());

		try (var c = BancoDeTeste.comoSuperusuario();
				var comando = c.createStatement();
				var linhas = comando.executeQuery("""
						SELECT count(*) FROM evento_auditoria
						 WHERE tenant_id = '%s' AND recurso = 'servico/%s'""".formatted(t.id(), id))) {
			linhas.next();
			assertThat(linhas.getLong(1)).isEqualTo(3);
		}
	}

	@Test
	void itensDeApoio() throws Exception {
		var grupo = json
			.readTree(enviar(post("/api/v1/catalogo/grupos"), """
					{"nome":"Água de reúso","ativo":true}""").andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigo").value("AGUA_DE_REUSO"))
				.andReturn()
				.getResponse()
				.getContentAsString())
			.get("id")
			.asString();
		enviar(post("/api/v1/catalogo/grupos"), """
				{"codigo":"agua de reuso","nome":"Repetido","ativo":true}""").andExpect(status().isConflict());
		enviar(put("/api/v1/catalogo/grupos/{id}", grupo), """
				{"codigo":"AGUA_DE_REUSO","nome":"Água de reúso (piloto)","ativo":false}""").andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(false));

		// Grupo com serviço ativo não pode ser inativado.
		var ligacoes = json
			.readTree(mvc.perform(get("/api/v1/catalogo").with(token(t, "sub-gestor-servicos")))
				.andReturn()
				.getResponse()
				.getContentAsString())
			.get("grupos");
		for (var g : ligacoes) {
			if (g.get("codigo").asString().equals("LIGACOES")) {
				enviar(put("/api/v1/catalogo/grupos/{id}", g.get("id").asString()), """
						{"codigo":"LIGACOES","nome":"Ligações","ativo":false}""").andExpect(status().isConflict());
			}
		}

		enviar(post("/api/v1/catalogo/veiculos"), """
				{"nome":"Retroescavadeira","perfilRota":"CAMINHAO","ativo":true}""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.codigo").value("RETROESCAVADEIRA"));
		enviar(post("/api/v1/catalogo/habilidades"), """
				{"nome":"Solda de PEAD","ativo":true}""").andExpect(status().isCreated());
		// Mesmo código em tipos diferentes de motivo é permitido; no mesmo tipo, não.
		enviar(post("/api/v1/catalogo/motivos"), """
				{"tipo":"PARALISACAO","nome":"Imóvel fechado","exigeObservacao":false,"ativo":true}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.codigo").value("IMOVEL_FECHADO"));
		enviar(post("/api/v1/catalogo/motivos"), """
				{"tipo":"DEVOLUCAO","nome":"Imóvel fechado","exigeObservacao":false,"ativo":true}""")
			.andExpect(status().isConflict());
	}

	private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido,
			String corpo) throws Exception {
		return mvc.perform(
				pedido.contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(t, "sub-gestor-servicos")));
	}

	private static RequestPostProcessor token(Tenant tenant, String sub) {
		return jwt().jwt(j -> j.issuer(tenant.emissor()).subject(sub));
	}

}
