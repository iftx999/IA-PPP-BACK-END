package dev.aurora.acesso.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.aurora.acesso.service.ContextoAcessoService;
import dev.aurora.comum.EnderecoDoEmissor;
import dev.aurora.contexto.ContextoAcesso;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

	/**
	 * Porta de gerenciamento (ADR 0022): saúde e métricas para o orquestrador e o
	 * Prometheus, numa porta que não passa pelo proxy público. Só existe quando a porta
	 * de gerenciamento é outra; qualquer outra rota ali é negada.
	 */
	@Bean
	@Order(0)
	@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
	SecurityFilterChain gerenciamento(HttpSecurity http) throws Exception {
		return http.securityMatcher(EndpointRequest.toAnyEndpoint())
			.authorizeHttpRequests(
					regras -> regras.requestMatchers(EndpointRequest.to("health", "prometheus", "info", "sbom"))
						.permitAll()
						.anyRequest()
						.denyAll())
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.build();
	}

	/**
	 * Administração da plataforma (S1, ADR 0027): só o emissor do realm da plataforma,
	 * que nenhum cliente usa. Chaves buscadas na primeira requisição (a API sobe sem o
	 * Keycloak).
	 */
	@Bean
	@Order(1)
	SecurityFilterChain plataforma(HttpSecurity http, @Value("${aurora.plataforma.emissor}") String emissor,
			@Value("${aurora.plataforma.segundo-fator:true}") boolean segundoFator,
			@Value("${aurora.seguranca.audiencia}") String audiencia,
			@Value("${aurora.identidade.url-interna:}") String urlInterna) throws Exception {
		var decodificador = new DecodificadorPreguicoso(() -> decodificador(emissor, audiencia, urlInterna));
		return http.securityMatcher("/api/v1/plataforma/**")
			.authorizeHttpRequests(regras -> regras.anyRequest().authenticated())
			.oauth2ResourceServer(servidor -> servidor.jwt(jwt -> jwt.decoder(decodificador)))
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.cors(cors -> {
			})
			.addFilterAfter(new FiltroContextoPlataforma(emissor, segundoFator), BearerTokenAuthenticationFilter.class)
			.build();
	}

	/**
	 * Antes do login (ADR 0027): o código da empresa leva ao emissor onde entrar, e a
	 * logo. Token, se vier, é ignorado.
	 */
	@Bean
	@Order(1)
	SecurityFilterChain publico(HttpSecurity http) throws Exception {
		return http.securityMatcher("/api/v1/publico/**")
			.authorizeHttpRequests(regras -> regras.requestMatchers(org.springframework.http.HttpMethod.GET, "/**")
				.permitAll()
				.anyRequest()
				.denyAll())
			.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.cors(cors -> {
			})
			.build();
	}

	/** Decodificador de um emissor: assinatura, emissor e audiência da API. */
	static JwtDecoder decodificador(String emissor, String audiencia, String urlInterna) {
		var decodificador = urlInterna.isBlank() ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(emissor)
				: NimbusJwtDecoder
					.withJwkSetUri(
							EnderecoDoEmissor.paraServidor(emissor, urlInterna) + "/protocol/openid-connect/certs")
					.build();
		decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(emissor),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audiencia))));
		return decodificador;
	}

	/**
	 * Cria o decodificador na primeira requisição (o Keycloak pode subir depois da API).
	 */
	static final class DecodificadorPreguicoso implements JwtDecoder {

		private final java.util.function.Supplier<JwtDecoder> fabrica;

		private volatile @org.jspecify.annotations.Nullable JwtDecoder alvo;

		DecodificadorPreguicoso(java.util.function.Supplier<JwtDecoder> fabrica) {
			this.fabrica = fabrica;
		}

		@Override
		public Jwt decode(String token) {
			var atual = alvo;
			if (atual == null) {
				synchronized (this) {
					atual = alvo;
					if (atual == null) {
						atual = fabrica.get();
						alvo = atual;
					}
				}
			}
			return atual.decode(token);
		}

	}

	@Bean
	@Order(2)
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
			@Value("${aurora.seguranca.audiencia}") String audiencia,
			@Value("${aurora.identidade.url-interna:}") String urlInterna) {
		Map<String, AuthenticationManager> porEmissor = new ConcurrentHashMap<>();
		return new JwtIssuerAuthenticationManagerResolver(emissor -> {
			if (resolvedor.tenantDoEmissor(emissor).isEmpty()) {
				return null;
			}
			// Chaves pela rede interna quando configurada; o emissor (iss) continua o
			// público.
			return porEmissor.computeIfAbsent(emissor,
					e -> new JwtAuthenticationProvider(decodificador(e, audiencia, urlInterna))::authenticate);
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
