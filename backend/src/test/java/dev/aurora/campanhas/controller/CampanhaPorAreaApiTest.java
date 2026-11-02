package dev.aurora.campanhas.controller;

import java.time.LocalDate;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR 0036: campanha por área do mapa e por setor, criada por quem gerencia só a Cidade A
 * e não vê dados pessoais (matrícula mascarada); busca de pessoas para a equipe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CampanhaPorAreaApiTest {

	private static Tenant t;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@BeforeAll
	static void dados() throws Exception {
		t = DadosDeTeste.criarTenant("Campanha por área");
		DadosDeTeste.criarAdesao(t);
		DadosDeTeste.criarHistoricoDeImoveis(t);
		var gestor = DadosDeTeste.criarPerfil(t, "Gestor", "campanha:gerenciar", "indicador:ler");
		DadosDeTeste.criarUsuario(t, "sub-gestor", "gestor@area.teste", t.cidadeA(), gestor);
		DadosDeTeste.criarUsuario(t, "sub-joana", "joana.campo@area.teste", t.cidadeA(), gestor);
		// 2001002 e 2001003 dentro do retângulo; 2001004 fora; os demais sem ponto.
		try (var c = BancoDeTeste.comoSuperusuario(); var comando = c.createStatement()) {
			for (var ponto : new String[][] { { "2001002", "-47.06", "-22.90" }, { "2001003", "-47.05", "-22.91" },
					{ "2001004", "-46.50", "-23.50" } }) {
				comando.execute("""
						INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao)
						VALUES ('%s', '%s', '%s', ST_SetSRID(ST_MakePoint(%s, %s), 4674), 'IMPORTADA', 'ENDERECO')"""
					.formatted(t.id(), ponto[0], t.setorA1(), ponto[1], ponto[2]));
			}
		}
	}

	@Test
	void areaDoMapaSetorEMatriculaMascarada() throws Exception {
		var area = """
				{"origem":"AREA_DO_MAPA","unidadeId":"%s","area":{"oeste":-47.2,"sul":-23.0,"leste":-47.0,"norte":-22.8}}"""
			.formatted(t.cidadeA());
		postar("/api/v1/campanhas/previa", area, "sub-gestor").andExpect(status().isOk())
			.andExpect(jsonPath("$.imoveis").value(2))
			.andExpect(jsonPath("$.impacto.unidadeNome").value("MUNICIPIO " + t.cidadeA()));
		postar("/api/v1/campanhas/previa", area.replace("-47.0,", "-47.3,"), "sub-gestor")
			.andExpect(status().isBadRequest());

		var hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
		var campanha = json
			.readTree(postar("/api/v1/campanhas",
					"""
							{"nome":"Vistoria do centro","recorte":%s,"acao":"VISTORIA","prazo":"%s"}""".formatted(area,
							hoje.plusDays(10)),
					"sub-gestor")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.recorte.area.oeste").value(-47.2))
				.andReturn()
				.getResponse()
				.getContentAsString())
			.get("id")
			.asString();
		mvc.perform(get("/api/v1/campanhas/{id}/ordens", campanha).with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.itens[0].matricula").value("••••002"))
			.andExpect(jsonPath("$.itens[0].tipo").value("VISTORIA"))
			.andExpect(jsonPath("$.itens[0].latitude").value(-22.90));

		// Por setor: só cortados, ficam de fora os que já têm OS aberta.
		postar("/api/v1/campanhas/previa", """
				{"origem":"SETOR","unidadeId":"%s","situacao":"FACTIVEL"}""".formatted(t.setorA1()), "sub-gestor")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imoveis").value(2))
			.andExpect(jsonPath("$.comOsAberta").value(2));
		postar("/api/v1/campanhas/previa", """
				{"origem":"SETOR","unidadeId":"%s","situacao":"FACTIVEL","motivoCorte":"A_PEDIDO"}"""
			.formatted(t.setorA1()), "sub-gestor").andExpect(status().isUnprocessableContent());

		mvc.perform(get("/api/v1/equipes/pessoas").param("busca", "joana").with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].email").value("joana.campo@area.teste"));
		mvc.perform(get("/api/v1/campanhas").with(token("sub-gestor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].nome").value("Vistoria do centro"))
			.andExpect(jsonPath("$[0].funil.planejadas").value(2));
	}

	private ResultActions postar(String url, String corpo, String sub) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corpo).with(token(sub)));
	}

	private static RequestPostProcessor token(String sub) {
		return jwt().jwt(j -> j.issuer(t.emissor()).subject(sub));
	}

}
