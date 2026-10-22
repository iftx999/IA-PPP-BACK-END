package dev.aurora.integracoes.config;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.integracoes.repository.CredencialRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Cadeia própria para a API do Agente Aurora (/api/agente/**): credencial do conector em
 * vez de token OIDC; vem antes da cadeia principal.
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoSegurancaAgente {

	@Bean
	@Order(1)
	SecurityFilterChain apiDoAgente(HttpSecurity http, CredencialRepository credenciais, ContextosDoSistema contextos,
			@Value("${aurora.agente.limite-por-minuto:60}") int limite) throws Exception {
		return http.securityMatcher("/api/agente/**")
			.authorizeHttpRequests(regras -> regras.anyRequest().authenticated())
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Sem cookie de sessão (credencial no cabeçalho): CSRF não se aplica.
			.csrf(csrf -> csrf.disable())
			.addFilterBefore(new FiltroAgente(credenciais, contextos, limite), AnonymousAuthenticationFilter.class)
			.build();
	}

}
