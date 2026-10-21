package dev.aurora.acesso.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.acesso.service.ContextoAcessoService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfiguracaoSeguranca {

	@Bean
	SecurityFilterChain api(HttpSecurity http, AuthenticationManagerResolver<HttpServletRequest> porEmissor,
			ContextoAcessoService resolvedor) throws Exception {
		return http.authorizeHttpRequests(regras -> regras.requestMatchers("/actuator/health/**")
			.permitAll()
			// Documentação da API: só existe fora de produção (springdoc desligado no
			// perfil prod).
			.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
			.permitAll()
			.anyRequest()
			.authenticated())
			.oauth2ResourceServer(servidor -> servidor.authenticationManagerResolver(porEmissor))
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// API sem cookie de sessão (token Bearer): CSRF não se aplica.
			.csrf(csrf -> csrf.disable())
			.cors(cors -> {
			})
			.addFilterAfter(new FiltroContextoAcesso(resolvedor), BearerTokenAuthenticationFilter.class)
			.build();
	}

	/**
	 * Cada tenant tem seu emissor OIDC (realm). Só emissores cadastrados em
	 * tenant_emissor são aceitos, e o token precisa ter a audiência da API.
	 */
	@Bean
	AuthenticationManagerResolver<HttpServletRequest> autenticacaoPorEmissor(ContextoAcessoService resolvedor,
			@Value("${aurora.seguranca.audiencia}") String audiencia) {
		Map<String, AuthenticationManager> porEmissor = new ConcurrentHashMap<>();
		return new JwtIssuerAuthenticationManagerResolver(emissor -> {
			if (resolvedor.tenantDoEmissor(emissor).isEmpty()) {
				return null;
			}
			return porEmissor.computeIfAbsent(emissor, e -> {
				var decodificador = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(e);
				decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
						JwtValidators.createDefaultWithIssuer(e), new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
								aud -> aud != null && aud.contains(audiencia))));
				return new JwtAuthenticationProvider(decodificador)::authenticate;
			});
		});
	}

	@Bean
	CorsConfigurationSource cors(@Value("${aurora.cors.origens}") List<String> origens) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(origens);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		var fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/api/**", config);
		return fonte;
	}

}
