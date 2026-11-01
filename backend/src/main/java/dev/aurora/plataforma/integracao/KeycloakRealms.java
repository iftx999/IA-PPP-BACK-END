package dev.aurora.plataforma.integracao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dev.aurora.comum.EnderecoDoEmissor;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import dev.aurora.plataforma.config.ConfiguracaoPlataforma;
import dev.aurora.plataforma.service.ProvedorDeRealms;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cria o realm do cliente pela Admin API do Keycloak, autenticada no realm master pela
 * conta {@code aurora-plataforma-admin}, que só tem create-realm (ADR 0027). O modelo é o
 * realm de produção gerado por {@code infra/keycloak/gerar-realm-producao.py}; os
 * {@code ${VAR}} recebem a configuração da própria API (mesmo segredo da conta de serviço
 * e mesmo SMTP).
 */
@Component
class KeycloakRealms implements ProvedorDeRealms {

	private static final Logger LOG = LoggerFactory.getLogger(KeycloakRealms.class);

	private static final String INDISPONIVEL = "O provedor de identidade não respondeu. Tente novamente em instantes.";

	private static final Pattern VARIAVEL = Pattern.compile("\\$\\{([A-Z_]+)}");

	private final RestClient http;

	private final ConfiguracaoPlataforma.Propriedades propriedades;

	private final @Nullable String urlInterna;

	private final Map<String, String> valores;

	private final String modelo;

	private final JsonMapper json;

	private volatile @Nullable Token token;

	private record Token(String valor, Instant expiraEm) {
	}

	private record RespostaToken(String access_token, long expires_in) {
	}

	@Autowired
	KeycloakRealms(ConfiguracaoPlataforma.Propriedades propriedades, JsonMapper json,
			@Value("${aurora.identidade.url-interna:}") @Nullable String urlInterna,
			@Value("${aurora.identidade.segredo}") String segredoDaApi,
			@Value("${aurora.identidade.url-retorno}") String urlFront,
			@Value("${aurora.plataforma.smtp-host:${aurora.email.host:}}") String smtpHost,
			@Value("${aurora.plataforma.smtp-porta:${aurora.email.porta:587}}") String smtpPorta,
			@Value("${aurora.email.usuario:}") String smtpUsuario, @Value("${aurora.email.senha:}") String smtpSenha,
			@Value("${aurora.email.remetente:nao-responda@aurora.localhost}") String smtpRemetente,
			@Value("${aurora.email.tls:true}") boolean smtpTls) {
		this(clienteHttp(propriedades.tempoLimite()), propriedades, json, urlInterna,
				valores(segredoDaApi, urlFront, smtpHost, smtpPorta, smtpUsuario, smtpSenha, smtpRemetente, smtpTls));
	}

	KeycloakRealms(RestClient http, ConfiguracaoPlataforma.Propriedades propriedades, JsonMapper json,
			@Nullable String urlInterna, Map<String, String> valores) {
		this.http = http;
		this.propriedades = propriedades;
		this.json = json;
		this.urlInterna = urlInterna;
		this.valores = Map.copyOf(valores);
		try {
			this.modelo = new ClassPathResource("keycloak/realm-modelo.json")
				.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException erro) {
			throw new UncheckedIOException("Modelo de realm ausente (keycloak/realm-modelo.json)", erro);
		}
	}

	private static RestClient clienteHttp(Duration tempoLimite) {
		var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		var fabrica = new JdkClientHttpRequestFactory(cliente);
		fabrica.setReadTimeout(tempoLimite);
		return RestClient.builder().requestFactory(fabrica).build();
	}

	/** Valores dos ${VAR} do modelo, vindos da configuração da própria API. */
	static Map<String, String> valores(String segredoDaApi, String urlFront, String smtpHost, String smtpPorta,
			String smtpUsuario, String smtpSenha, String smtpRemetente, boolean smtpTls) {
		var mapa = new LinkedHashMap<String, String>();
		mapa.put("AURORA_URL_FRONT", urlFront.replaceAll("/+$", ""));
		mapa.put("AURORA_IDENTIDADE_SEGREDO", segredoDaApi);
		mapa.put("AURORA_SMTP_HOST", smtpHost);
		mapa.put("AURORA_SMTP_PORTA", smtpPorta);
		mapa.put("AURORA_SMTP_USUARIO", smtpUsuario);
		mapa.put("AURORA_SMTP_SENHA", smtpSenha);
		mapa.put("AURORA_SMTP_REMETENTE", smtpRemetente);
		mapa.put("AURORA_SMTP_AUTENTICAR", String.valueOf(!smtpUsuario.isBlank()));
		mapa.put("AURORA_SMTP_TLS", String.valueOf(smtpTls));
		return mapa;
	}

	@Override
	public String emissor(String codigo) {
		return propriedades.urlIdentidade().replaceAll("/+$", "") + "/realms/" + codigo;
	}

	@Override
	public boolean existe(String codigo) {
		return chamar(() -> {
			try {
				http.get()
					.uri(admin() + "/realms/{realm}", codigo)
					.headers(h -> h.setBearerAuth(token()))
					.retrieve()
					.toBodilessEntity();
				return true;
			}
			catch (HttpClientErrorException.NotFound naoExiste) {
				return false;
			}
			catch (HttpClientErrorException.Forbidden deOutroDono) {
				// Existe, mas não foi criado pela plataforma (ex.: o master ou um realm
				// antigo).
				return true;
			}
		});
	}

	@Override
	public boolean criar(String codigo, String nomeCliente) {
		var realm = preencher(codigo, nomeCliente);
		return chamar(() -> {
			try {
				http.post()
					.uri(admin() + "/realms")
					.headers(h -> h.setBearerAuth(token()))
					.contentType(MediaType.APPLICATION_JSON)
					.body(realm)
					.retrieve()
					.toBodilessEntity();
				LOG.info("Realm {} criado", codigo);
				return true;
			}
			catch (HttpClientErrorException.Conflict jaExiste) {
				return false;
			}
		});
	}

	/** Troca os ${VAR} do modelo; os valores entram como texto JSON escapado. */
	String preencher(String codigo, String nomeCliente) {
		var todos = new LinkedHashMap<>(valores);
		todos.put("AURORA_REALM", codigo);
		todos.put("AURORA_NOME_CLIENTE", nomeCliente);
		var saida = new StringBuilder();
		var achado = VARIAVEL.matcher(modelo);
		while (achado.find()) {
			var valor = todos.get(achado.group(1));
			if (valor == null) {
				throw new IllegalStateException("Variável sem valor no modelo de realm: " + achado.group(1));
			}
			var escapado = json.writeValueAsString(valor);
			achado.appendReplacement(saida,
					java.util.regex.Matcher.quoteReplacement(escapado.substring(1, escapado.length() - 1)));
		}
		achado.appendTail(saida);
		return saida.toString();
	}

	private String token() {
		var atual = token;
		if (atual != null && Instant.now().isBefore(atual.expiraEm())) {
			return atual.valor();
		}
		var formulario = new LinkedMultiValueMap<String, String>();
		formulario.add("grant_type", "client_credentials");
		formulario.add("client_id", propriedades.clienteId());
		formulario.add("client_secret", propriedades.segredo() == null ? "" : propriedades.segredo());
		var resposta = http.post()
			.uri(servidor() + "/realms/master/protocol/openid-connect/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(formulario)
			.retrieve()
			.body(RespostaToken.class);
		if (resposta == null) {
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, null);
		}
		var novo = new Token(resposta.access_token(),
				Instant.now().plusSeconds(Math.max(0, resposta.expires_in() - 30)));
		token = novo;
		return novo.valor();
	}

	/** Endereço do Keycloak para a API: o interno, se configurado. */
	private String servidor() {
		var publico = propriedades.urlIdentidade().replaceAll("/+$", "");
		return EnderecoDoEmissor.paraServidor(publico + "/realms/master", urlInterna).replaceAll("/realms/master$", "");
	}

	private String admin() {
		return servidor() + "/admin";
	}

	private <T> T chamar(java.util.function.Supplier<T> chamada) {
		try {
			return chamada.get();
		}
		catch (HttpClientErrorException erro) {
			token = null;
			LOG.error("Provedor de identidade recusou a chamada da plataforma: {}", erro.getStatusCode());
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, erro);
		}
		catch (RestClientException erro) {
			LOG.error("Falha ao chamar o provedor de identidade", erro);
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, erro);
		}
	}

}
