package dev.aurora.acesso.integracao;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.acesso.model.ProvedorCorporativo.Protocolo;
import dev.aurora.acesso.service.ProvedorIdentidade;
import dev.aurora.comum.EnderecoDoEmissor;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Keycloak Admin REST API. Cada tenant tem um realm (o emissor); a API se autentica nele
 * com client credentials e guarda o token até perto de expirar.
 */
public class KeycloakProvedorIdentidade implements ProvedorIdentidade {

	private static final Logger LOG = LoggerFactory.getLogger(KeycloakProvedorIdentidade.class);

	private static final String INDISPONIVEL = "O provedor de identidade não respondeu. Tente novamente em instantes.";

	private final RestClient http;

	private final ConfiguracaoIdentidade.Propriedades propriedades;

	private final Map<String, TokenDeServico> tokens = new ConcurrentHashMap<>();

	KeycloakProvedorIdentidade(RestClient.Builder http, ConfiguracaoIdentidade.Propriedades propriedades) {
		this.http = http.build();
		this.propriedades = propriedades;
	}

	record TokenDeServico(String valor, Instant expiraEm) {
	}

	record RespostaToken(String access_token, long expires_in) {
	}

	record Conta(String id) {
	}

	record ContaComAcoes(String id, @Nullable List<String> requiredActions) {
	}

	record Credencial(String id, String type, long createdDate) {
	}

	record IdpKc(String alias, String providerId, @Nullable String displayName, boolean enabled,
			@Nullable Map<String, String> config) {
	}

	/** Fluxos do realm para o SSO corporativo (ADR 0025). */
	static final String FLUXO_PRIMEIRO_LOGIN = "aurora-sso-primeiro-login";

	static final String FLUXO_POS_LOGIN = "aurora-sso-pos-login";

	/**
	 * Valor que o Keycloak devolve no lugar de segredos; enviado de volta, mantém o
	 * atual.
	 */
	static final String SEGREDO_MASCARADO = "**********";

	private static final String ENDERECO_INVALIDO = "Não foi possível ler a configuração do provedor neste endereço. "
			+ "Confira se é o documento de descoberta OIDC (.well-known/openid-configuration) ou os metadados SAML "
			+ "do provedor e se ele está acessível.";

	/** Atributos de e-mail mais comuns em asserções SAML (genérico e Entra ID/ADFS). */
	private static final List<String> ATRIBUTOS_EMAIL_SAML = List.of("email",
			"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");

	/** Tipo da credencial TOTP e ação obrigatória que pede o cadastro no Keycloak. */
	private static final String OTP = "otp";

	private static final String CONFIGURAR_OTP = "CONFIGURE_TOTP";

	@Override
	public String criarConta(String emissor, String email, String nome) {
		var partes = nome.strip().split("\\s+", 2);
		var conta = Map.of("username", email, "email", email, "firstName", partes[0], "lastName",
				partes.length > 1 ? partes[1] : "", "enabled", true, "emailVerified", false);
		return executar(() -> {
			try {
				var resposta = http.post()
					.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/users")
					.headers(h -> h.setBearerAuth(token(emissor)))
					.contentType(MediaType.APPLICATION_JSON)
					.body(conta)
					.retrieve()
					.toBodilessEntity();
				var local = resposta.getHeaders().getLocation();
				if (local == null) {
					throw new ServicoExternoIndisponivelException(INDISPONIVEL, null);
				}
				var caminho = local.getPath();
				return caminho.substring(caminho.lastIndexOf('/') + 1);
			}
			catch (HttpClientErrorException.Conflict jaExiste) {
				return buscarPorEmail(emissor, email);
			}
		});
	}

	@Override
	public void enviarConvite(String emissor, String sub) {
		enviarAcoes(emissor, sub, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
	}

	@Override
	public void enviarRedefinicaoSenha(String emissor, String sub) {
		enviarAcoes(emissor, sub, List.of("UPDATE_PASSWORD"));
	}

	@Override
	public void definirAtiva(String emissor, String sub, boolean ativa) {
		executar(() -> {
			try {
				http.put()
					.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/users/{id}", sub)
					.headers(h -> h.setBearerAuth(token(emissor)))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("enabled", ativa))
					.retrieve()
					.toBodilessEntity();
				if (!ativa) {
					http.post()
						.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna()))
								+ "/users/{id}/logout", sub)
						.headers(h -> h.setBearerAuth(token(emissor)))
						.retrieve()
						.toBodilessEntity();
				}
			}
			catch (HttpClientErrorException.NotFound semConta) {
				// Conta já removida no provedor: não há login a bloquear.
				LOG.warn("Conta {} não existe mais no provedor de identidade", sub);
			}
			return null;
		});
	}

	@Override
	public Optional<Instant> segundoFatorCadastradoEm(String emissor, String sub) {
		var cadastro = executar(() -> Arrays.stream(credenciais(emissor, sub))
			.filter(c -> OTP.equals(c.type()))
			.map(c -> Instant.ofEpochMilli(c.createdDate()))
			.max(Instant::compareTo));
		return cadastro == null ? Optional.empty() : cadastro;
	}

	@Override
	public void exigirCadastroSegundoFator(String emissor, String sub) {
		executar(() -> {
			if (Arrays.stream(credenciais(emissor, sub)).anyMatch(c -> OTP.equals(c.type()))) {
				return null;
			}
			var conta = http.get()
				.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/users/{id}", sub)
				.headers(h -> h.setBearerAuth(token(emissor)))
				.retrieve()
				.body(ContaComAcoes.class);
			var acoes = new ArrayList<String>(
					conta == null || conta.requiredActions() == null ? List.of() : conta.requiredActions());
			if (!acoes.contains(CONFIGURAR_OTP)) {
				acoes.add(CONFIGURAR_OTP);
				http.put()
					.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/users/{id}", sub)
					.headers(h -> h.setBearerAuth(token(emissor)))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("requiredActions", acoes))
					.retrieve()
					.toBodilessEntity();
			}
			return null;
		});
	}

	@Override
	public void removerSegundoFator(String emissor, String sub) {
		executar(() -> {
			for (var credencial : credenciais(emissor, sub)) {
				if (OTP.equals(credencial.type())) {
					http.delete()
						.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna()))
								+ "/users/{id}/credentials/{credencial}", sub, credencial.id())
						.headers(h -> h.setBearerAuth(token(emissor)))
						.retrieve()
						.toBodilessEntity();
				}
			}
			return null;
		});
	}

	@Override
	public Optional<ProvedorCorporativo> provedorCorporativo(String emissor) {
		var provedor = executar(() -> idpCorporativo(emissor).map(KeycloakProvedorIdentidade::paraModelo));
		return provedor == null ? Optional.empty() : provedor;
	}

	@Override
	public void salvarProvedorCorporativo(String emissor, ProvedorCorporativo provedor, @Nullable String segredo) {
		var tipo = provedor.protocolo().name().toLowerCase(java.util.Locale.ROOT);
		executar(() -> {
			var config = new LinkedHashMap<String, String>(importarConfiguracao(emissor, tipo, provedor.endereco()));
			config.put("metadataDescriptorUrl", provedor.endereco());
			config.put("syncMode", "IMPORT");
			if (provedor.protocolo() == Protocolo.OIDC) {
				config.put("clientId", String.valueOf(provedor.clienteId()));
				config.put("clientSecret", segredo != null ? segredo : SEGREDO_MASCARADO);
				config.put("clientAuthMethod", "client_secret_post");
				config.put("defaultScope", "openid email profile");
				config.put("pkceEnabled", "true");
				config.put("pkceMethod", "S256");
			}
			else {
				config.put("nameIDPolicyFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
				config.put("principalType", "SUBJECT");
			}
			var corpo = new LinkedHashMap<String, Object>();
			corpo.put("alias", ProvedorCorporativo.APELIDO);
			corpo.put("displayName", provedor.nome());
			corpo.put("providerId", tipo);
			corpo.put("enabled", provedor.habilitado());
			// O e-mail vem do diretório da empresa; só serve para achar a conta já
			// convidada.
			corpo.put("trustEmail", true);
			corpo.put("storeToken", false);
			corpo.put("firstBrokerLoginFlowAlias", FLUXO_PRIMEIRO_LOGIN);
			corpo.put("postBrokerLoginFlowAlias", FLUXO_POS_LOGIN);
			corpo.put("config", config);

			var existente = idpCorporativo(emissor);
			var instancias = idps(emissor) + "/instances";
			if (existente.isPresent() && existente.get().providerId().equals(tipo)) {
				http.put()
					.uri(instancias + "/{alias}", ProvedorCorporativo.APELIDO)
					.headers(h -> h.setBearerAuth(token(emissor)))
					.contentType(MediaType.APPLICATION_JSON)
					.body(corpo)
					.retrieve()
					.toBodilessEntity();
				return null;
			}
			if (provedor.protocolo() == Protocolo.OIDC && segredo == null) {
				throw new IllegalArgumentException("Segredo obrigatório ao criar o IdP OIDC");
			}
			// Troca de protocolo: o tipo do IdP não muda no Keycloak, então recria.
			existente.ifPresent(e -> removerIdp(emissor));
			http.post()
				.uri(instancias)
				.headers(h -> h.setBearerAuth(token(emissor)))
				.contentType(MediaType.APPLICATION_JSON)
				.body(corpo)
				.retrieve()
				.toBodilessEntity();
			if (provedor.protocolo() == Protocolo.SAML) {
				for (var atributo : ATRIBUTOS_EMAIL_SAML) {
					http.post()
						.uri(instancias + "/{alias}/mappers", ProvedorCorporativo.APELIDO)
						.headers(h -> h.setBearerAuth(token(emissor)))
						.contentType(MediaType.APPLICATION_JSON)
						.body(Map.of("name", "email: " + atributo, "identityProviderAlias", ProvedorCorporativo.APELIDO,
								"identityProviderMapper", "saml-user-attribute-idp-mapper", "config",
								Map.of("syncMode", "INHERIT", "attribute.name", atributo, "user.attribute", "email")))
						.retrieve()
						.toBodilessEntity();
				}
			}
			return null;
		});
	}

	@Override
	public void removerProvedorCorporativo(String emissor) {
		executar(() -> {
			removerIdp(emissor);
			return null;
		});
	}

	private void removerIdp(String emissor) {
		try {
			http.delete()
				.uri(idps(emissor) + "/instances/{alias}", ProvedorCorporativo.APELIDO)
				.headers(h -> h.setBearerAuth(token(emissor)))
				.retrieve()
				.toBodilessEntity();
		}
		catch (HttpClientErrorException.NotFound jaRemovido) {
			// Nada a remover.
		}
	}

	private Optional<IdpKc> idpCorporativo(String emissor) {
		try {
			return Optional.ofNullable(http.get()
				.uri(idps(emissor) + "/instances/{alias}", ProvedorCorporativo.APELIDO)
				.headers(h -> h.setBearerAuth(token(emissor)))
				.retrieve()
				.body(IdpKc.class));
		}
		catch (HttpClientErrorException.NotFound semIdp) {
			return Optional.empty();
		}
	}

	/**
	 * O Keycloak lê o endereço e devolve a configuração (endpoints, chaves, certificado).
	 * Endereço inválido ou inacessível vira 400/500 lá.
	 */
	private Map<String, String> importarConfiguracao(String emissor, String tipo, String endereco) {
		Map<?, ?> lida;
		try {
			lida = http.post()
				.uri(idps(emissor) + "/import-config")
				.headers(h -> h.setBearerAuth(token(emissor)))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("providerId", tipo, "fromUrl", endereco))
				.retrieve()
				.body(Map.class);
		}
		catch (HttpServerErrorException | HttpClientErrorException.BadRequest erro) {
			throw new RegraVioladaException(ENDERECO_INVALIDO);
		}
		var essencial = "oidc".equals(tipo) ? "authorizationUrl" : "singleSignOnServiceUrl";
		if (lida == null || lida.get(essencial) == null) {
			throw new RegraVioladaException(ENDERECO_INVALIDO);
		}
		var config = new LinkedHashMap<String, String>();
		lida.forEach((chave, valor) -> {
			if (valor != null) {
				config.put(String.valueOf(chave), String.valueOf(valor));
			}
		});
		return config;
	}

	private static ProvedorCorporativo paraModelo(IdpKc idp) {
		var config = idp.config() == null ? Map.<String, String>of() : idp.config();
		var protocolo = "saml".equals(idp.providerId()) ? Protocolo.SAML : Protocolo.OIDC;
		return new ProvedorCorporativo(protocolo, idp.displayName() == null ? idp.alias() : idp.displayName(),
				config.getOrDefault("metadataDescriptorUrl", ""),
				protocolo == Protocolo.OIDC ? config.get("clientId") : null, idp.enabled());
	}

	private String idps(String emissor) {
		return admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/identity-provider";
	}

	private Credencial[] credenciais(String emissor, String sub) {
		var lista = http.get()
			.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna())) + "/users/{id}/credentials",
					sub)
			.headers(h -> h.setBearerAuth(token(emissor)))
			.retrieve()
			.body(Credencial[].class);
		return lista == null ? new Credencial[0] : lista;
	}

	private void enviarAcoes(String emissor, String sub, List<String> acoes) {
		URI uri = UriComponentsBuilder
			.fromUriString(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna()))
					+ "/users/{id}/execute-actions-email")
			.queryParam("client_id", propriedades.clienteWeb())
			.queryParam("redirect_uri", propriedades.urlRetorno())
			.queryParam("lifespan", propriedades.validadeConvite().toSeconds())
			.buildAndExpand(sub)
			.encode()
			.toUri();
		executar(() -> http.put()
			.uri(uri)
			.headers(h -> h.setBearerAuth(token(emissor)))
			.contentType(MediaType.APPLICATION_JSON)
			.body(acoes)
			.retrieve()
			.toBodilessEntity());
	}

	private String buscarPorEmail(String emissor, String email) {
		var contas = http.get()
			.uri(admin(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna()))
					+ "/users?email={email}&exact=true", email)
			.headers(h -> h.setBearerAuth(token(emissor)))
			.retrieve()
			.body(Conta[].class);
		if (contas == null || contas.length != 1) {
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, null);
		}
		return contas[0].id();
	}

	private String token(String emissor) {
		var atual = tokens.get(emissor);
		if (atual != null && Instant.now().isBefore(atual.expiraEm())) {
			return atual.valor();
		}
		var formulario = new LinkedMultiValueMap<String, String>();
		formulario.add("grant_type", "client_credentials");
		formulario.add("client_id", propriedades.clienteId());
		formulario.add("client_secret", propriedades.segredo());
		var resposta = http.post()
			.uri(EnderecoDoEmissor.paraServidor(emissor, propriedades.urlInterna()) + "/protocol/openid-connect/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(formulario)
			.retrieve()
			.body(RespostaToken.class);
		if (resposta == null) {
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, null);
		}
		// Margem de 30 s para não usar um token que expira no meio da chamada.
		var novo = new TokenDeServico(resposta.access_token(),
				Instant.now().plusSeconds(Math.max(0, resposta.expires_in() - 30)));
		tokens.put(emissor, novo);
		return novo.valor();
	}

	/** {@code https://idp/realms/x} -> {@code https://idp/admin/realms/x}. */
	static String admin(String emissor) {
		var indice = emissor.lastIndexOf("/realms/");
		if (indice < 0) {
			throw new IllegalArgumentException("Emissor não é um realm do Keycloak: " + emissor);
		}
		return emissor.substring(0, indice) + "/admin" + emissor.substring(indice);
	}

	private <T> @Nullable T executar(java.util.function.Supplier<T> chamada) {
		try {
			return chamada.get();
		}
		catch (HttpClientErrorException erro) {
			if (erro.getStatusCode() == HttpStatus.UNAUTHORIZED || erro.getStatusCode() == HttpStatus.FORBIDDEN) {
				// Token revogado ou com papéis desatualizados: a próxima chamada pede
				// outro.
				tokens.clear();
			}
			LOG.error("Provedor de identidade recusou a chamada: {}", erro.getStatusCode());
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, erro);
		}
		catch (RestClientException erro) {
			LOG.error("Falha ao chamar o provedor de identidade", erro);
			throw new ServicoExternoIndisponivelException(INDISPONIVEL, erro);
		}
	}

}
