package dev.aurora.acesso.integracao;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.acesso.service.ProvedorIdentidade;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
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

	@Override
	public String criarConta(String emissor, String email, String nome) {
		var partes = nome.strip().split("\\s+", 2);
		var conta = Map.of("username", email, "email", email, "firstName", partes[0], "lastName",
				partes.length > 1 ? partes[1] : "", "enabled", true, "emailVerified", false);
		return executar(() -> {
			try {
				var resposta = http.post()
					.uri(admin(emissor) + "/users")
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
					.uri(admin(emissor) + "/users/{id}", sub)
					.headers(h -> h.setBearerAuth(token(emissor)))
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("enabled", ativa))
					.retrieve()
					.toBodilessEntity();
				if (!ativa) {
					http.post()
						.uri(admin(emissor) + "/users/{id}/logout", sub)
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

	private void enviarAcoes(String emissor, String sub, List<String> acoes) {
		URI uri = UriComponentsBuilder.fromUriString(admin(emissor) + "/users/{id}/execute-actions-email")
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
			.uri(admin(emissor) + "/users?email={email}&exact=true", email)
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
			.uri(emissor + "/protocol/openid-connect/token")
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
