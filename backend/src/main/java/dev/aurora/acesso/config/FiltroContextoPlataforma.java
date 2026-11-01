package dev.aurora.acesso.config;

import java.io.IOException;
import java.util.Collection;

import dev.aurora.comum.RespostaDeProblema;
import dev.aurora.acesso.service.SegundoFatorNecessarioException;
import dev.aurora.contexto.ContextoPlataforma;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Administração da plataforma (ADR 0027): só tokens do realm da plataforma, sempre com o
 * código do aplicativo autenticador ({@code amr} com {@code otp}) fora do
 * desenvolvimento. O emissor é conferido de novo aqui (defesa em profundidade). Quem
 * passa age no contexto de plataforma, nunca no de um cliente.
 */
class FiltroContextoPlataforma extends OncePerRequestFilter {

	private final String emissor;

	private final boolean exigirSegundoFator;

	FiltroContextoPlataforma(String emissor, boolean exigirSegundoFator) {
		this.emissor = emissor;
		this.exigirSegundoFator = exigirSegundoFator;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken autenticacao)
				|| !emissor.equals(String.valueOf(autenticacao.getToken().getIssuer()))) {
			resposta.sendError(HttpStatus.FORBIDDEN.value(), "Somente a administração da plataforma");
			return;
		}
		var token = autenticacao.getToken();
		if (exigirSegundoFator
				&& !(token.getClaim("amr") instanceof Collection<?> metodos && metodos.contains("otp"))) {
			RespostaDeProblema.escrever(resposta, HttpStatus.FORBIDDEN,
					"A administração da plataforma exige o código do aplicativo autenticador.",
					SegundoFatorNecessarioException.CODIGO);
			return;
		}
		var operador = token.getSubject();
		if (operador == null || operador.isBlank()) {
			RespostaDeProblema.escrever(resposta, HttpStatus.UNAUTHORIZED, "Token sem identificação do operador.",
					null);
			return;
		}
		MDC.put("plataforma", "sim");
		try {
			ContextoPlataforma.executar(new ContextoPlataforma(operador), () -> {
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
		finally {
			MDC.remove("plataforma");
		}
	}

}
