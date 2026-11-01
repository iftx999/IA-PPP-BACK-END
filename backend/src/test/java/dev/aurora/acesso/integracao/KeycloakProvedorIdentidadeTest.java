package dev.aurora.acesso.integracao;

import java.net.URI;
import java.time.Duration;

import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.acesso.model.ProvedorCorporativo.Protocolo;
import dev.aurora.comum.RegraVioladaException;
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
				+ "/users/abc-123/execute-actions-email?client_id=aurora-web&redirect_uri=http://front.teste/?cliente%3Dcliente&lifespan=259200"))
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
					+ "&redirect_uri=http://front.teste/?cliente%3Dcliente&lifespan=259200"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> provedor.enviarRedefinicaoSenha(EMISSOR, "abc"))
			.isInstanceOf(ServicoExternoIndisponivelException.class);
	}

	@Test
	void segundoFatorCadastradoEhOTotpMaisRecente() {
		esperarToken();
		servidor.expect(requestTo(ADMIN + "/users/abc/credentials"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					[{"id":"c1","type":"password","createdDate":1793050000000},
					 {"id":"c2","type":"otp","createdDate":1793050100000},
					 {"id":"c3","type":"otp","createdDate":1793050200000}]""", MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(ADMIN + "/users/sem/credentials"))
			.andRespond(withSuccess("[{\"id\":\"c1\",\"type\":\"password\",\"createdDate\":1}]",
					MediaType.APPLICATION_JSON));

		assertThat(provedor.segundoFatorCadastradoEm(EMISSOR, "abc"))
			.contains(java.time.Instant.ofEpochMilli(1793050200000L));
		assertThat(provedor.segundoFatorCadastradoEm(EMISSOR, "sem")).isEmpty();
	}

	@Test
	void exigirCadastroAcrescentaAAcaoSemPerderAsOutras() {
		esperarToken();
		servidor.expect(requestTo(ADMIN + "/users/abc/credentials"))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(ADMIN + "/users/abc"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"id\":\"abc\",\"requiredActions\":[\"UPDATE_PASSWORD\"]}",
					MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(ADMIN + "/users/abc"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().json("{\"requiredActions\":[\"UPDATE_PASSWORD\",\"CONFIGURE_TOTP\"]}", true))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));
		// Quem já tem TOTP não recebe a ação.
		servidor.expect(requestTo(ADMIN + "/users/com/credentials"))
			.andRespond(withSuccess("[{\"id\":\"c\",\"type\":\"otp\",\"createdDate\":1}]", MediaType.APPLICATION_JSON));

		provedor.exigirCadastroSegundoFator(EMISSOR, "abc");
		provedor.exigirCadastroSegundoFator(EMISSOR, "com");
		servidor.verify();
	}

	@Test
	void removerSegundoFatorApagaSoOTotp() {
		esperarToken();
		servidor.expect(requestTo(ADMIN + "/users/abc/credentials"))
			.andRespond(withSuccess("""
					[{"id":"senha","type":"password","createdDate":1},{"id":"totp","type":"otp","createdDate":2}]""",
					MediaType.APPLICATION_JSON));
		servidor.expect(once(), requestTo(ADMIN + "/users/abc/credentials/totp"))
			.andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		provedor.removerSegundoFator(EMISSOR, "abc");
		servidor.verify();
	}

	@Test
	void emissorPrecisaSerUmRealm() {
		assertThatThrownBy(() -> KeycloakProvedorIdentidade.admin("http://idp.teste/outro"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static final String IDPS = ADMIN + "/identity-provider";

	private static final String DESCOBERTA = "https://login.empresa.teste/v2.0/.well-known/openid-configuration";

	@Test
	void criaIdpOidcComAConfiguracaoLidaDoEnderecoEOsFluxosDaAurora() {
		esperarToken();
		servidor.expect(once(), requestTo(IDPS + "/import-config"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"providerId\":\"oidc\",\"fromUrl\":\"" + DESCOBERTA + "\"}"))
			.andRespond(withSuccess(
					"""
							{"authorizationUrl":"https://login.empresa.teste/auth","tokenUrl":"https://login.empresa.teste/token",
							 "validateSignature":"true","useJwksUrl":true}""",
					MediaType.APPLICATION_JSON));
		servidor.expect(once(), requestTo(IDPS + "/instances/corporativo"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		servidor.expect(once(), requestTo(IDPS + "/instances"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content()
				.json("""
						{"alias":"corporativo","displayName":"Entra ID","providerId":"oidc","enabled":true,"trustEmail":true,
						 "storeToken":false,"firstBrokerLoginFlowAlias":"aurora-sso-primeiro-login",
						 "postBrokerLoginFlowAlias":"aurora-sso-pos-login",
						 "config":{"authorizationUrl":"https://login.empresa.teste/auth","useJwksUrl":"true",
						   "metadataDescriptorUrl":"%s","clientId":"app-aurora","clientSecret":"s3gr3do",
						   "clientAuthMethod":"client_secret_post","pkceEnabled":"true","syncMode":"IMPORT"}}"""
					.formatted(DESCOBERTA)))
			.andRespond(withStatus(HttpStatus.CREATED));

		provedor.salvarProvedorCorporativo(EMISSOR,
				new ProvedorCorporativo(Protocolo.OIDC, "Entra ID", DESCOBERTA, "app-aurora", true), "s3gr3do");
		servidor.verify();
	}

	@Test
	void atualizarSemSegredoMandaOValorMascaradoQueMantemOAtual() {
		esperarToken();
		servidor.expect(requestTo(IDPS + "/import-config"))
			.andRespond(withSuccess("{\"authorizationUrl\":\"https://x/auth\"}", MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(IDPS + "/instances/corporativo"))
			.andRespond(withSuccess("{\"alias\":\"corporativo\",\"providerId\":\"oidc\",\"enabled\":true}",
					MediaType.APPLICATION_JSON));
		servidor.expect(once(), requestTo(IDPS + "/instances/corporativo"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(content().json("{\"displayName\":\"Novo nome\",\"enabled\":false,"
					+ "\"config\":{\"clientSecret\":\"**********\"}}"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		provedor.salvarProvedorCorporativo(EMISSOR,
				new ProvedorCorporativo(Protocolo.OIDC, "Novo nome", DESCOBERTA, "app-aurora", false), null);
		servidor.verify();
	}

	@Test
	void trocarParaSamlRecriaOIdpComOsMapeadoresDeEmail() {
		esperarToken();
		servidor.expect(requestTo(IDPS + "/import-config"))
			.andExpect(content().json("{\"providerId\":\"saml\"}"))
			.andRespond(withSuccess("{\"singleSignOnServiceUrl\":\"https://x/saml\",\"signingCertificate\":\"MII\"}",
					MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(IDPS + "/instances/corporativo"))
			.andRespond(withSuccess("{\"alias\":\"corporativo\",\"providerId\":\"oidc\",\"enabled\":true}",
					MediaType.APPLICATION_JSON));
		servidor.expect(once(), requestTo(IDPS + "/instances/corporativo"))
			.andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));
		servidor.expect(once(), requestTo(IDPS + "/instances"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
					{"providerId":"saml","config":{"singleSignOnServiceUrl":"https://x/saml","signingCertificate":"MII",
					 "nameIDPolicyFormat":"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"}}"""))
			.andRespond(withStatus(HttpStatus.CREATED));
		servidor
			.expect(org.springframework.test.web.client.ExpectedCount.times(2),
					requestTo(IDPS + "/instances/corporativo/mappers"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"identityProviderMapper\":\"saml-user-attribute-idp-mapper\"}"))
			.andRespond(withStatus(HttpStatus.CREATED));

		provedor.salvarProvedorCorporativo(EMISSOR,
				new ProvedorCorporativo(Protocolo.SAML, "ADFS", "https://x/metadata", null, true), null);
		servidor.verify();
	}

	@Test
	void enderecoSemConfiguracaoValidaViraRegraVioladaSemCriarNada() {
		esperarToken();
		servidor.expect(requestTo(IDPS + "/import-config")).andRespond(withServerError());
		servidor.expect(requestTo(IDPS + "/import-config"))
			.andRespond(withSuccess("{\"issuer\":\"https://x\"}", MediaType.APPLICATION_JSON));
		var provedorX = new ProvedorCorporativo(Protocolo.OIDC, "X", "https://x/nada", "c", true);

		assertThatThrownBy(() -> provedor.salvarProvedorCorporativo(EMISSOR, provedorX, "s"))
			.isInstanceOf(RegraVioladaException.class)
			.hasMessageContaining("descoberta OIDC");
		// Responde, mas não é um documento de descoberta (sem authorizationUrl).
		assertThatThrownBy(() -> provedor.salvarProvedorCorporativo(EMISSOR, provedorX, "s"))
			.isInstanceOf(RegraVioladaException.class);
		servidor.verify();
	}

	@Test
	void leOIdpSemOSegredoEIgnoraIdpInexistenteAoRemover() {
		esperarToken();
		servidor.expect(requestTo(IDPS + "/instances/corporativo"))
			.andRespond(withSuccess("""
					{"alias":"corporativo","displayName":"Entra ID","providerId":"oidc","enabled":true,
					 "config":{"clientId":"app","clientSecret":"**********","metadataDescriptorUrl":"%s"}}"""
				.formatted(DESCOBERTA), MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(IDPS + "/instances/corporativo")).andRespond(withStatus(HttpStatus.NOT_FOUND));
		servidor.expect(requestTo(IDPS + "/instances/corporativo"))
			.andExpect(method(HttpMethod.DELETE))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(provedor.provedorCorporativo(EMISSOR))
			.contains(new ProvedorCorporativo(Protocolo.OIDC, "Entra ID", DESCOBERTA, "app", true));
		assertThat(provedor.provedorCorporativo(EMISSOR)).isEmpty();
		provedor.removerProvedorCorporativo(EMISSOR);
		servidor.verify();
	}

	private void esperarToken() {
		servidor.expect(once(), requestTo(EMISSOR + "/protocol/openid-connect/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials", "client_id",
					"aurora-api-admin", "client_secret", "segredo")))
			.andRespond(withSuccess("{\"access_token\":\"tk\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
	}

}
