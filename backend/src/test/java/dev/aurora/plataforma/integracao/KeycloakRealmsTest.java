package dev.aurora.plataforma.integracao;

import java.time.Duration;

import dev.aurora.plataforma.config.ConfiguracaoPlataforma;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakRealmsTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private MockRestServiceServer servidor;

	private KeycloakRealms realms;

	@BeforeEach
	void montar() {
		var builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		var propriedades = new ConfiguracaoPlataforma.Propriedades("https://id.teste/realms/plataforma", true,
				"https://id.teste/", "aurora-plataforma-admin", "segredo-plataforma", Duration.ofSeconds(30));
		realms = new KeycloakRealms(builder.build(), propriedades, json, "http://keycloak:8080",
				KeycloakRealms.valores("api-\"secreto\"", "https://aurora.teste/", "smtp.teste", "587", "", "",
						"nao-responda@aurora.teste", true));
	}

	@Test
	void preencheOModeloComEscapeJson() {
		var realm = json.readTree(realms.preencher("grupo-x", "Grupo \"X\" & Cia"));
		assertThat(realm.get("realm").asString()).isEqualTo("grupo-x");
		assertThat(realm.get("displayName").asString()).isEqualTo("Grupo \"X\" & Cia");
		assertThat(realm.get("smtpServer").get("auth").asString()).isEqualTo("false");
		assertThat(realm.get("smtpServer").get("starttls").asString()).isEqualTo("true");
		assertThat(realm.toString()).doesNotContain("${");
		var api = realm.get("clients")
			.valueStream()
			.filter(c -> c.get("clientId").asString().equals("aurora-api-admin"))
			.findFirst()
			.orElseThrow();
		assertThat(api.get("secret").asString()).isEqualTo("api-\"secreto\"");
		var web = realm.get("clients")
			.valueStream()
			.filter(c -> c.get("clientId").asString().equals("aurora-web"))
			.findFirst()
			.orElseThrow();
		assertThat(web.get("redirectUris").get(0).asString()).isEqualTo("https://aurora.teste/*");
	}

	@Test
	void criaPeloEnderecoInternoComAContaDoMasterEReconheceRealmExistente() {
		assertThat(realms.emissor("grupo-x")).isEqualTo("https://id.teste/realms/grupo-x");
		servidor.expect(requestTo("http://keycloak:8080/realms/master/protocol/openid-connect/token"))
			.andExpect(content().formDataContains(
					java.util.Map.of("grant_type", "client_credentials", "client_id", "aurora-plataforma-admin")))
			.andRespond(withSuccess("{\"access_token\":\"tk\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
		servidor.expect(requestTo("http://keycloak:8080/admin/realms"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer tk"))
			.andRespond(withStatus(HttpStatus.CREATED));
		servidor.expect(requestTo("http://keycloak:8080/admin/realms")).andRespond(withStatus(HttpStatus.CONFLICT));
		servidor.expect(requestTo("http://keycloak:8080/admin/realms/de-outro"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN));
		servidor.expect(requestTo("http://keycloak:8080/admin/realms/livre"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(realms.criar("grupo-x", "Grupo X")).isTrue();
		assertThat(realms.criar("grupo-x", "Grupo X")).isFalse();
		assertThat(realms.existe("de-outro")).isTrue();
		assertThat(realms.existe("livre")).isFalse();
		servidor.verify();
	}

}
