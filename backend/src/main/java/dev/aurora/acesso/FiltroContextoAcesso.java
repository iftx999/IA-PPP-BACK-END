package dev.aurora.acesso;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Depois da autenticação do token, resolve o contexto de acesso e troca as authorities pelas permissões do banco.
 * Token válido de pessoa que não é usuária ativa do tenant recebe 403.
 */
class FiltroContextoAcesso extends OncePerRequestFilter {

	private final ResolvedorContexto resolvedor;

	FiltroContextoAcesso(ResolvedorContexto resolvedor) {
		this.resolvedor = resolvedor;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken autenticacao)) {
			cadeia.doFilter(requisicao, resposta);
			return;
		}
		ContextoAcesso contexto;
		try {
			contexto = resolvedor.resolver(autenticacao.getToken()).orElse(null);
		}
		catch (Exception erro) {
			throw new ServletException("falha ao resolver o contexto de acesso", erro);
		}
		if (contexto == null) {
			resposta.sendError(HttpStatus.FORBIDDEN.value(), "Usuário sem acesso a este tenant");
			return;
		}

		var autoridades = contexto.permissoes().stream().map(SimpleGrantedAuthority::new).toList();
		var comPermissoes = new JwtAuthenticationToken(autenticacao.getToken(), autoridades, autenticacao.getName());
		SecurityContextHolder.getContext().setAuthentication(comPermissoes);
		try {
			ContextoAcesso.executar(contexto, () -> {
				cadeia.doFilter(requisicao, resposta);
				return null;
			});
		}
		catch (IOException | ServletException | RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new ServletException(erro);
		}
	}

}
