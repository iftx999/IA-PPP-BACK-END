package dev.aurora.comum;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentação OpenAPI 3 da API (desligada no perfil prod). */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoOpenApi {

	@Bean
	OpenAPI documentacao() {
		return new OpenAPI()
			.info(new Info().title("Aurora API")
				.version("v1")
				.description("Adesão à rede de esgoto em concessões de saneamento. "
						+ "Tenant e escopo vêm do token; recurso fora do escopo responde 404."))
			.components(new Components().addSecuritySchemes("bearer",
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

}
