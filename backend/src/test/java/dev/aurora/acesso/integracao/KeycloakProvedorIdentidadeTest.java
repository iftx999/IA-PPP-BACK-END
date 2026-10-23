package dev.aurora.acesso.integracao;

import java.net.URI;
import java.time.Duration;

import dev.aurora.comum.ServicoExternoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakProvedorIdentidadeTest {

	private static final String EMISSOR = "http://idp.teste/realms/cliente";

	private static final String ADMIN = "http://idp.teste/admin/realms/cliente";

	private MockRestServiceServer servidor;

	private KeycloakProvedorIdentidade provedor;

	@BeforeEach
	void montar() {
		var builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		provedor = new KeycloakProvedorIdentidade(builder, new ConfiguracaoIdentidade.Propriedades("aurora-api-admin",
				"segredo", "aurora-web", "http://front.teste/", Duration.ofHours(72), null));
	}

	@Test
	void criaContaComNomeDivididoEReusaOToken() {
		esperarToken();
		servidor.expect(once(), requestTo(ADMIN + "/users"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tk"))
			.andExpect(content().json("""
					{"username":"ana@x.dev","email":"ana@x.dev","firstName":"Ana","lastName":"Maria Souza",
					 "enabled":true,"emailVerified":false}"""))
			.andRespond(withStatus(HttpStatus.CREATED).location(URI.create(ADMIN + "/users/abc-123")));
		servidor.expect(once(), requestTo(ADMIN
				+ "/users/abc-123/execute-actions-email?client_id=aurora-web&redirect_uri=http://front.teste/&lifespan=259200"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().json("[\"VERIFY_EMAIL\",\"UPDATE_PASSWORD\"]"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		assertThat(provedor.criarConta(EMISSOR, "ana@x.dev", " Ana  Maria Souza ")).isEqualTo("abc-123");
		provedor.enviarConvite(EMISSOR, "abc-123");
		servidor.verify();
	}

	@Test
	void contaExistenteEReaproveitadaPeloEmail() {
		esperarToken();
		servidor.expect(requestTo(ADMIN + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));
		servidor.expect(requestTo(ADMIN + "/users?email=ana%40x.dev&exact=true"))
			.andRespond(withSuccess("[{\"id\":\"ja-existe\"}]", MediaType.APPLICATION_JSON));

		assertThat(provedor.criarConta(EMISSOR, "ana@x.dev", "Ana")).isEqualTo("ja-existe");
	}

	@Test
	void desativarBloqueiaEEncerraSessoes() {
		esperarToken();
		servidor.expect(requestTo(ADMIN + "/users/abc"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().json("{\"enabled\":false}"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));
		servidor.expect(requestTo(ADMIN + "/users/abc/logout"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		provedor.definirAtiva(EMISSOR, "abc", false);
		servidor.verify();
	}

	@Test
	void falhaDoProvedorViraServicoIndisponivel() {
		esperarToken();
		servidor
			.expect(requestTo(ADMIN + "/users/abc/execute-actions-email?client_id=aurora-web"
					+ "&redirect_uri=http://front.teste/&lifespan=259200"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> provedor.enviarRedefinicaoSenha(EMISSOR, "abc"))
			.isInstanceOf(ServicoExternoIndisponivelException.class);
	}

	@Test
	void emissorPrecisaSerUmRealm() {
		assertThatThrownBy(() -> KeycloakProvedorIdentidade.admin("http://idp.teste/outro"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private void esperarToken() {
		servidor.expect(once(), requestTo(EMISSOR + "/protocol/openid-connect/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials", "client_id",
					"aurora-api-admin", "client_secret", "segredo")))
			.andRespond(withSuccess("{\"access_token\":\"tk\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
	}

}
