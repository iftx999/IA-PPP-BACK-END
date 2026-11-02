package dev.aurora.plataforma;

import java.util.List;
import java.util.UUID;

import dev.aurora.BancoDeTeste;
import dev.aurora.DadosDeTeste;
import dev.aurora.DadosDeTeste.Tenant;
import dev.aurora.TestcontainersConfiguration;
import dev.aurora.acesso.service.ProvedorIdentidade;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import dev.aurora.plataforma.service.ProvedorDeRealms;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administração da plataforma (S1, ADR 0027) com o provedor de identidade simulado e o
 * segundo fator obrigatório (como em produção).
 */
@SpringBootTest(properties = { "aurora.plataforma.emissor=https://idp.teste/realms/plataforma",
		"aurora.plataforma.segundo-fator=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlataformaApiTest {

	private static final String PLATAFORMA = "https://idp.teste/realms/plataforma";

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13 };

	private static Tenant existente;

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@MockitoBean
	ProvedorDeRealms realms;

	@MockitoBean
	ProvedorIdentidade identidade;

	@BeforeAll
	static void dados() throws Exception {
		existente = DadosDeTeste.criarTenant("Cliente existente");
		DadosDeTeste.criarUsuario(existente, "sub-admin-existente", "admin@existente.teste", existente.grupo());
	}

	@BeforeEach
	void provedores() {
		reset(realms, identidade);
		when(realms.emissor(anyString())).thenAnswer(i -> "https://idp.teste/realms/" + i.getArgument(0));
		when(realms.criar(anyString(), anyString())).thenReturn(true);
		when(identidade.criarConta(anyString(), anyString(), anyString())).thenAnswer(i -> "sub-" + i.getArgument(1));
	}

	@Test
	void soAPlataformaComCodigoEntraESoNaPlataforma() throws Exception {
		// Administrador de um cliente (com todas as permissões dele) não administra a
		// plataforma.
		mvc.perform(get("/api/v1/plataforma/clientes").with(jwt().jwt(
				j -> j.issuer(existente.emissor()).subject("sub-admin-existente").claim("amr", List.of("pwd", "otp")))))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/plataforma/clientes").with(plataforma(List.of("pwd"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo", is("SEGUNDO_FATOR_NECESSARIO")));
		mvc.perform(get("/api/v1/plataforma/clientes").with(plataforma()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].nome", hasItem("Cliente existente")));
		// E o token da plataforma não serve dentro de um cliente.
		mvc.perform(get("/api/v1/eu").with(plataforma())).andExpect(status().isForbidden());
	}

	@Test
	void criaClienteComRealmRaizPerfisEConviteDoPrimeiroAdministrador() throws Exception {
		var codigo = "grupo-" + UUID.randomUUID().toString().substring(0, 8);
		var id = criar(codigo, "admin@" + codigo + ".teste").andExpect(status().isCreated())
			.andExpect(jsonPath("$.provisionado", is(true)))
			.andExpect(jsonPath("$.emissor", is("https://idp.teste/realms/" + codigo)))
			.andExpect(jsonPath("$.usuariosAtivos", is(1)))
			.andExpect(jsonPath("$.limites.usuarios", is(5)))
			.andReturn();
		var cliente = UUID.fromString(json.readTree(id.getResponse().getContentAsString()).get("id").asString());

		verify(realms).criar(codigo, "Grupo " + codigo);
		var emissor = "https://idp.teste/realms/" + codigo;
		verify(identidade).criarConta(emissor, "admin@" + codigo + ".teste", "Ana Administradora");
		verify(identidade).enviarConvite(emissor, "sub-admin@" + codigo + ".teste");
		assertThat(consulta("SELECT count(*) FROM perfil WHERE sistema AND tenant_id = ?", cliente)).isEqualTo(5);
		assertThat(consulta("""
				SELECT count(*) FROM usuario_perfil_escopo e JOIN perfil p ON p.id = e.perfil_id
				  JOIN unidade u ON u.id = e.unidade_id
				 WHERE e.tenant_id = ? AND p.nome = 'Administrador do tenant' AND u.unidade_pai_id IS NULL""", cliente))
			.isEqualTo(1);
		assertThat(consulta("SELECT count(*) FROM evento_auditoria WHERE tenant_id = ? AND tipo = 'CLIENTE_CRIADO'",
				cliente))
			.isEqualTo(1);

		// O primeiro administrador entra no cliente novo (emissor dele) e já administra.
		mvc.perform(get("/api/v1/usuarios").with(jwt()
			.jwt(j -> j.issuer(emissor).subject("sub-admin@" + codigo + ".teste").claim("amr", List.of("pwd", "otp")))))
			.andExpect(status().isOk());

		// Código repetido e código de realm existente (de outro dono) não são aceitos.
		criar(codigo, "outro@x.teste").andExpect(status().isConflict());
		when(realms.existe("realm-alheio")).thenReturn(true);
		criar("realm-alheio", "outro@x.teste").andExpect(status().isConflict());
		for (var invalido : List.of("ab", "acentuação", "-hifen", "com espaco", "plataforma", "master")) {
			criar(invalido, "outro@x.teste").andExpect(status().isUnprocessableContent());
		}
	}

	@Test
	void provedorForaDoArDeixaOProvisionamentoPendenteParaConcluirDepois() throws Exception {
		var codigo = "pendente-" + UUID.randomUUID().toString().substring(0, 8);
		doThrow(new ServicoExternoIndisponivelException("fora do ar", null)).when(realms)
			.criar(eq(codigo), anyString());
		criar(codigo, "admin@" + codigo + ".teste").andExpect(status().isServiceUnavailable());
		var cliente = consultaId("SELECT id FROM tenant WHERE codigo = ?", codigo);
		mvc.perform(get("/api/v1/plataforma/clientes/{id}", cliente).with(plataforma()))
			.andExpect(jsonPath("$.provisionado", is(false)));
		verify(identidade, never()).enviarConvite(anyString(), anyString());
		// Ainda não aparece na tela de entrada.
		mvc.perform(get("/api/v1/publico/clientes/{codigo}", codigo)).andExpect(status().isNotFound());

		reset(realms);
		when(realms.emissor(anyString())).thenAnswer(i -> "https://idp.teste/realms/" + i.getArgument(0));
		mvc.perform(post("/api/v1/plataforma/clientes/{id}/provisionamento", cliente).with(plataforma()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provisionado", is(true)));
		verify(identidade).enviarConvite(anyString(), eq("sub-admin@" + codigo + ".teste"));
		// Rodar de novo não duplica nada.
		mvc.perform(post("/api/v1/plataforma/clientes/{id}/provisionamento", cliente).with(plataforma()))
			.andExpect(status().isOk());
		assertThat(consulta("SELECT count(*) FROM usuario WHERE tenant_id = ?", cliente)).isEqualTo(1);
		assertThat(consulta("SELECT count(*) FROM perfil WHERE tenant_id = ?", cliente)).isEqualTo(5);
	}

	@Test
	void limitesSituacaoLogoETelaDeEntrada() throws Exception {
		var codigo = "limites-" + UUID.randomUUID().toString().substring(0, 8);
		var resposta = criar(codigo, "admin@" + codigo + ".teste").andReturn();
		var cliente = UUID.fromString(json.readTree(resposta.getResponse().getContentAsString()).get("id").asString());
		var adminDoCliente = jwt().jwt(j -> j.issuer("https://idp.teste/realms/" + codigo)
			.subject("sub-admin@" + codigo + ".teste")
			.claim("amr", List.of("pwd", "otp")));

		// Limite de 1 usuário: o administrador já ocupa a vaga, o convite seguinte é
		// recusado.
		alterar(cliente, "Grupo " + codigo, true, 1).andExpect(status().isOk());
		var unidade = consultaId("SELECT id FROM unidade WHERE tenant_id = ? AND unidade_pai_id IS NULL", cliente);
		var perfil = consultaId("SELECT id FROM perfil WHERE tenant_id = ? AND nome = 'Diretor'", cliente);
		mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"Bia","email":"bia@%s.teste","concessoes":[{"perfilId":"%s","unidadeId":"%s"}]}"""
				.formatted(codigo, perfil, unidade))
			.with(adminDoCliente))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail", containsString("limite de 1 usuários")));
		alterar(cliente, "Grupo " + codigo, true, null).andExpect(status().isOk());

		// Logo conferida pelo conteúdo; aparece na tela de entrada.
		mvc.perform(multipart("/api/v1/plataforma/clientes/{id}/logo", cliente)
			.file(new MockMultipartFile("arquivo", "logo.png", "image/png", "<svg/>".getBytes()))
			.with(r -> {
				r.setMethod("PUT");
				return r;
			})
			.with(plataforma())).andExpect(status().isUnprocessableContent());
		mvc.perform(multipart("/api/v1/plataforma/clientes/{id}/logo", cliente)
			.file(new MockMultipartFile("arquivo", "logo.png", "application/octet-stream", PNG))
			.with(r -> {
				r.setMethod("PUT");
				return r;
			})
			.with(plataforma())).andExpect(status().isOk()).andExpect(jsonPath("$.temLogo", is(true)));
		mvc.perform(get("/api/v1/publico/clientes/{codigo}", codigo))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome", is("Grupo " + codigo)))
			.andExpect(jsonPath("$.emissor", is("https://idp.teste/realms/" + codigo)))
			.andExpect(jsonPath("$.temLogo", is(true)))
			.andExpect(jsonPath("$.contatoEmail").doesNotExist());
		mvc.perform(get("/api/v1/publico/clientes/{codigo}/logo", codigo))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/png"))
			.andExpect(content().bytes(PNG));

		// Cliente desativado: ninguém dele entra, e some da tela de entrada.
		alterar(cliente, "Grupo " + codigo, false, null).andExpect(status().isOk());
		mvc.perform(get("/api/v1/eu").with(adminDoCliente)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/publico/clientes/{codigo}", codigo)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/publico/clientes/{codigo}", "nao-existe")).andExpect(status().isNotFound());
		assertThat(consulta("SELECT count(*) FROM evento_auditoria WHERE tenant_id = ? AND tipo = 'CLIENTE_ALTERADO'",
				cliente))
			.isEqualTo(4);
	}

	private ResultActions criar(String codigo, String email) throws Exception {
		return mvc.perform(post("/api/v1/plataforma/clientes").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"codigo":"%s","nome":"Grupo %s","contatoNome":"Ana Administradora","contatoEmail":"%s",
					 "limites":{"usuarios":5,"assistenteTokensPorMes":1000000}}""".formatted(codigo, codigo, email))
			.with(plataforma()));
	}

	private ResultActions alterar(UUID cliente, String nome, boolean ativo, Integer limite) throws Exception {
		return mvc.perform(put("/api/v1/plataforma/clientes/{id}", cliente).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nome":"%s","ativo":%s,"limites":{"usuarios":%s}}""".formatted(nome, ativo, limite))
			.with(plataforma()));
	}

	private static RequestPostProcessor plataforma() {
		return plataforma(List.of("pwd", "otp"));
	}

	private static RequestPostProcessor plataforma(List<String> amr) {
		return jwt().jwt(j -> j.issuer(PLATAFORMA).subject("sub-operador").claim("amr", amr));
	}

	private static long consulta(String sql, Object parametro) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.prepareStatement(sql)) {
			consulta.setObject(1, parametro);
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getLong(1);
		}
	}

	private static UUID consultaId(String sql, Object parametro) throws Exception {
		try (var c = BancoDeTeste.comoSuperusuario(); var consulta = c.prepareStatement(sql)) {
			consulta.setObject(1, parametro);
			var linhas = consulta.executeQuery();
			linhas.next();
			return linhas.getObject(1, UUID.class);
		}
	}

}
