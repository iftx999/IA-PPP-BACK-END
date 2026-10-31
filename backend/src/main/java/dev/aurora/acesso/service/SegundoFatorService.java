package dev.aurora.acesso.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.comum.ServicoExternoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Verificação em duas etapas (H9, RN-17). Quem já cadastrou o TOTP tem o código pedido
 * pelo Keycloak em todo login; aqui se confere, quando a política exige, que o token foi
 * emitido com ele (claim {@code amr} com {@code otp}). Quem ainda não cadastrou recebe a
 * ação obrigatória de cadastro e entra de novo.
 *
 * <p>
 * O login em que a pessoa cadastra o TOTP sai com {@code amr} só com senha, embora ela
 * tenha digitado um código válido para concluir o cadastro. Esse token é aceito quando o
 * cadastro aconteceu depois do {@code auth_time} e dentro de {@link #JANELA_CADASTRO}.
 */
@Service
public class SegundoFatorService {

	private static final Logger LOG = LoggerFactory.getLogger(SegundoFatorService.class);

	static final String METODO_OTP = "otp";

	/** Tempo entre a senha e o fim do cadastro do TOTP no mesmo login. */
	static final Duration JANELA_CADASTRO = Duration.ofMinutes(10);

	/** Quanto tempo vale a conferência feita no provedor para o mesmo login. */
	private static final Duration VALIDADE_ACEITO = Duration.ofMinutes(15);

	private static final Duration VALIDADE_RECUSADO = Duration.ofSeconds(30);

	private static final int LIMITE_CONFERENCIAS = 10_000;

	private final ProvedorIdentidade identidade;

	private final Clock relogio;

	private final boolean exigirDeAdministradores;

	/** Por login ({@code sub} + {@code auth_time}): aceito ou não, e até quando vale. */
	private final Map<String, Conferencia> conferencias = new ConcurrentHashMap<>();

	private record Conferencia(boolean aceito, Instant validaAte) {
	}

	SegundoFatorService(ProvedorIdentidade identidade, Clock relogio,
			@Value("${aurora.seguranca.segundo-fator-administradores:true}") boolean exigirDeAdministradores) {
		this.identidade = identidade;
		this.relogio = relogio;
		this.exigirDeAdministradores = exigirDeAdministradores;
	}

	/** Administradores (gestão de usuários) sempre precisam do segundo fator. */
	public boolean exigidoDeAdministradores() {
		return exigirDeAdministradores;
	}

	public boolean exigido(boolean politicaDoTenant, boolean administrador) {
		return politicaDoTenant || (exigirDeAdministradores && administrador);
	}

	/**
	 * @param confiaNoIdpCorporativo o tenant aceita a verificação feita pelo IdP
	 * corporativo (ADR 0025): login por ele dispensa o TOTP da Aurora
	 * @throws SegundoFatorNecessarioException se o token não comprova o segundo fator
	 */
	public void exigir(Jwt token, boolean confiaNoIdpCorporativo) {
		if (comOtp(token) || (confiaNoIdpCorporativo && pelaEmpresa(token))) {
			return;
		}
		var authTime = authTime(token);
		var chave = token.getSubject() + "@" + authTime;
		var agora = relogio.instant();
		var conferencia = conferencias.get(chave);
		if (conferencia == null || conferencia.validaAte().isBefore(agora)) {
			conferencia = conferir(token, authTime, agora);
			if (conferencias.size() >= LIMITE_CONFERENCIAS) {
				conferencias.values().removeIf(c -> c.validaAte().isBefore(agora));
			}
			conferencias.put(chave, conferencia);
		}
		if (!conferencia.aceito()) {
			throw new SegundoFatorNecessarioException();
		}
	}

	private Conferencia conferir(Jwt token, Instant authTime, Instant agora) {
		var emissor = String.valueOf(token.getIssuer());
		try {
			var cadastro = identidade.segundoFatorCadastradoEm(emissor, token.getSubject());
			if (cadastro.isPresent() && !cadastro.get().isBefore(authTime)
					&& cadastro.get().isBefore(authTime.plus(JANELA_CADASTRO))) {
				return new Conferencia(true, agora.plus(VALIDADE_ACEITO));
			}
			if (cadastro.isEmpty()) {
				identidade.exigirCadastroSegundoFator(emissor, token.getSubject());
			}
		}
		catch (ServicoExternoIndisponivelException erro) {
			// Sem o provedor não há como conferir: recusa (falha fechada).
			LOG.warn("Segundo fator não conferido: provedor de identidade indisponível");
		}
		return new Conferencia(false, agora.plus(VALIDADE_RECUSADO));
	}

	/** Claim {@code idp}: nota de sessão do Keycloak com o IdP usado no login. */
	private static boolean pelaEmpresa(Jwt token) {
		return ProvedorCorporativo.APELIDO.equals(token.getClaimAsString("idp"));
	}

	private static boolean comOtp(Jwt token) {
		return token.getClaim("amr") instanceof Collection<?> metodos && metodos.contains(METODO_OTP);
	}

	/** {@code auth_time} em segundos (OIDC); sem ele, o token não serve de prova. */
	private static Instant authTime(Jwt token) {
		Object valor = token.getClaim("auth_time");
		if (valor instanceof Instant instante) {
			return instante;
		}
		if (valor instanceof Number segundos) {
			return Instant.ofEpochSecond(segundos.longValue());
		}
		return Instant.MAX;
	}

}
