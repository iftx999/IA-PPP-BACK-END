package dev.aurora.acesso.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Evento de acesso lido do provedor de identidade (ADR 0028). Nunca traz o que a pessoa
 * digitou no login: quando a conta não existe, {@code sub} fica nulo.
 *
 * @param id identificador do evento no provedor (evita importar duas vezes)
 * @param sub conta envolvida, se identificada
 * @param motivo código do erro no provedor (ex.: {@code invalid_user_credentials})
 * @param cliente aplicação onde o login aconteceu (ex.: {@code aurora-web})
 * @param provedorCorporativo IdP usado no login por SSO, se houver
 */
public record EventoDeAcesso(String id, Instant ocorridoEm, Tipo tipo, @Nullable String sub, @Nullable String ip,
		@Nullable String motivo, @Nullable String cliente, @Nullable String provedorCorporativo) {

	public enum Tipo {

		LOGIN, FALHA_DE_LOGIN, BLOQUEIO_TEMPORARIO, BLOQUEIO_PERMANENTE, LOGOUT

	}

}
