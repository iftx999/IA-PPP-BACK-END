package dev.aurora.integracoes.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.repository.CredencialRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica o Agente Aurora pela credencial "prefixo.segredo" (Bearer): confere o SHA-256
 * em tempo constante, limita a taxa por credencial e executa a requisição no contexto do
 * tenant da credencial. A fonte vem da credencial, nunca do corpo.
 */
class FiltroAgente extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(FiltroAgente.class);

	private final CredencialRepository credenciais;

	private final ContextosDoSistema contextos;

	private final int limitePorMinuto;

	FiltroAgente(CredencialRepository credenciais, ContextosDoSistema contextos, int limitePorMinuto) {
		this.credenciais = credenciais;
		this.contextos = contextos;
		this.limitePorMinuto = limitePorMinuto;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		var cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);
		var chave = cabecalho != null && cabecalho.startsWith("Bearer ") ? cabecalho.substring(7).strip() : "";
		var ponto = chave.indexOf('.');
		if (ponto <= 0 || ponto == chave.length() - 1) {
			recusar(resposta, HttpStatus.UNAUTHORIZED, "Credencial do agente ausente ou mal formada.");
			return;
		}
		var prefixo = chave.substring(0, ponto);
		var credencial = credenciais.buscarAtiva(prefixo)
			.filter(c -> MessageDigest.isEqual(sha256(chave.substring(ponto + 1)), c.hash()));
		if (credencial.isEmpty()) {
			LOG.warn("Credencial de agente recusada (prefixo {})", prefixo.length() > 20 ? "?" : prefixo);
			recusar(resposta, HttpStatus.UNAUTHORIZED, "Credencial do agente inválida ou revogada.");
			return;
		}
		if (credenciais.contarRequisicao("agente:" + prefixo) > limitePorMinuto) {
			resposta.setHeader(HttpHeaders.RETRY_AFTER, "60");
			recusar(resposta, HttpStatus.TOO_MANY_REQUESTS, "Limite de requisições do agente atingido.");
			return;
		}
		var contexto = contextos.doTenant(credencial.get().tenantId()).orElse(null);
		if (contexto == null) {
			recusar(resposta, HttpStatus.UNAUTHORIZED, "Cliente inativo.");
			return;
		}
		requisicao.setAttribute(AgenteAutenticado.ATRIBUTO_FONTE, credencial.get().fonteId());
		requisicao.setAttribute(AgenteAutenticado.ATRIBUTO_PREFIXO, prefixo);
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("agente:" + prefixo, null,
					List.of(new SimpleGrantedAuthority("AGENTE"))));
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
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private static void recusar(HttpServletResponse resposta, HttpStatus status, String detalhe) throws IOException {
		resposta.setStatus(status.value());
		resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resposta.getWriter()
			.write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"}".formatted(status.value(),
					status.getReasonPhrase(), detalhe));
	}

	private static byte[] sha256(String texto) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException erro) {
			throw new IllegalStateException(erro);
		}
	}

}
