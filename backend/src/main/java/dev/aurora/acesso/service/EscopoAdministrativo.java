package dev.aurora.acesso.service;

import dev.aurora.contexto.ContextoAcesso;

import org.springframework.security.access.AccessDeniedException;

/**
 * Perfis e usuários afetam o tenant inteiro: só quem atua na unidade raiz pode geri-los.
 */
final class EscopoAdministrativo {

	private EscopoAdministrativo() {
	}

	static ContextoAcesso exigirTenantInteiro() {
		var contexto = ContextoAcesso.exigir();
		if (!contexto.abrangeTenantInteiro()) {
			throw new AccessDeniedException("A administração de acesso exige escopo no tenant inteiro.");
		}
		return contexto;
	}

}
