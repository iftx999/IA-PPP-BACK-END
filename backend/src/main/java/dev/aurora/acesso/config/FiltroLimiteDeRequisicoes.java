package dev.aurora.acesso.config;

import java.io.IOException;

import dev.aurora.comum.RespostaDeProblema;
import dev.aurora.contexto.ContextoAcesso;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de requisições por pessoa autenticada (ADR 0037), depois do contexto de acesso:
 * a chave é tenant + usuário, nunca o IP (escritórios inteiros saem pelo mesmo IP). O
 * limite por IP fica na borda (nginx do front).
 */
class FiltroLimiteDeRequisicoes extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(FiltroLimiteDeRequisicoes.class);

	private final LimitadorDeRequisicoes limitador;

	FiltroLimiteDeRequisicoes(LimitadorDeRequisicoes limitador) {
		this.limitador = limitador;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		var contexto = ContextoAcesso.atual().orElse(null);
		if (contexto == null || contexto.usuarioId() == null) {
			cadeia.doFilter(requisicao, resposta);
			return;
		}
		var chave = contexto.tenantId() + ":" + contexto.usuarioId();
		if (!limitador.permitir(chave)) {
			LOG.warn("Limite de requisições por minuto atingido (usuário {})", contexto.usuarioId());
			resposta.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(limitador.segundosAteLiberar()));
			RespostaDeProblema.escrever(resposta, HttpStatus.TOO_MANY_REQUESTS,
					"Muitas requisições em pouco tempo. Aguarde alguns segundos e tente de novo.", null);
			return;
		}
		cadeia.doFilter(requisicao, resposta);
	}

}
