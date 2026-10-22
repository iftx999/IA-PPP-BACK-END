package dev.aurora.acesso.config;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contexto.ExigePermissao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica {@link ExigePermissao}: confere a permissão e restringe o escopo da requisição
 * às unidades onde ela vale, restaurando o contexto anterior no fim.
 */
class InterceptadorPermissao implements HandlerInterceptor {

	private static final String ANTERIOR = InterceptadorPermissao.class.getName() + ".anterior";

	@Override
	public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler) {
		var permissao = permissaoExigida(handler);
		if (permissao == null) {
			return true;
		}
		var contexto = ContextoAcesso.atual().orElseThrow(() -> new AccessDeniedException("Sem contexto de acesso."));
		if (!contexto.pode(permissao)) {
			throw new AccessDeniedException("Permissão necessária: " + permissao);
		}
		requisicao.setAttribute(ANTERIOR, contexto);
		ContextoAcesso.trocar(contexto.restritoA(permissao));
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler,
			@Nullable Exception erro) {
		if (requisicao.getAttribute(ANTERIOR) instanceof ContextoAcesso anterior) {
			ContextoAcesso.trocar(anterior);
			requisicao.removeAttribute(ANTERIOR);
		}
	}

	static @Nullable String permissaoExigida(Object handler) {
		if (!(handler instanceof HandlerMethod metodo)) {
			return null;
		}
		var anotacao = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), ExigePermissao.class);
		if (anotacao == null) {
			anotacao = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), ExigePermissao.class);
		}
		return anotacao == null ? null : anotacao.value();
	}

}
