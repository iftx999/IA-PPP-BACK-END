package dev.aurora.integracoes.config;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Fonte e credencial do agente autenticado, gravadas na requisição pelo
 * {@link FiltroAgente}.
 */
public final class AgenteAutenticado {

	static final String ATRIBUTO_FONTE = AgenteAutenticado.class.getName() + ".fonte";

	static final String ATRIBUTO_PREFIXO = AgenteAutenticado.class.getName() + ".prefixo";

	private AgenteAutenticado() {
	}

	public static UUID fonte(HttpServletRequest requisicao) {
		return (UUID) requisicao.getAttribute(ATRIBUTO_FONTE);
	}

	public static String prefixo(HttpServletRequest requisicao) {
		return (String) requisicao.getAttribute(ATRIBUTO_PREFIXO);
	}

}
