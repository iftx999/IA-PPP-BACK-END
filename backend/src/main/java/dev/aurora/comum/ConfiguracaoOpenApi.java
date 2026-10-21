package dev.aurora.comum;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/** Documentação OpenAPI 3.1 da API (desligada no perfil prod). */
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

	/**
	 * Contrato preciso para os clientes gerados: nos records da API, todo componente é
	 * obrigatório e não nulo, exceto os anotados com {@link Nullable}, que viram
	 * {@code tipo | null}.
	 */
	@Bean
	OpenApiCustomizer camposObrigatoriosPelosRecords() {
		var records = recordsDaAplicacao();
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
				return;
			}
			openApi.getComponents().getSchemas().forEach((nome, esquema) -> {
				var tipo = records.get(nome);
				if (tipo != null && esquema.getProperties() != null) {
					ajustar(esquema, tipo);
				}
			});
		};
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void ajustar(Schema esquema, Class<?> tipo) {
		var obrigatorios = new ArrayList<String>();
		Map<String, Schema> propriedades = esquema.getProperties();
		for (RecordComponent componente : tipo.getRecordComponents()) {
			var propriedade = propriedades.get(componente.getName());
			if (propriedade == null) {
				continue;
			}
			obrigatorios.add(componente.getName());
			if (componente.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
				propriedades.put(componente.getName(), comNulo(propriedade));
			}
		}
		esquema.setRequired(obrigatorios);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Schema comNulo(Schema propriedade) {
		if (propriedade.get$ref() != null) {
			var nulo = new Schema<>();
			nulo.addType("null");
			return new Schema<>().anyOf(List.of(new Schema<>().$ref(propriedade.get$ref()), nulo));
		}
		if (propriedade.getTypes() != null) {
			propriedade.addType("null");
		}
		return propriedade;
	}

	/**
	 * Records de dev.aurora pelo nome simples, que é como o springdoc nomeia os esquemas.
	 */
	private static Map<String, Class<?>> recordsDaAplicacao() {
		var varredura = new ClassPathScanningCandidateComponentProvider(false);
		varredura.addIncludeFilter(
				(leitor, fabrica) -> Record.class.getName().equals(leitor.getClassMetadata().getSuperClassName()));
		var porNome = new HashMap<String, Class<?>>();
		var repetidos = new HashSet<String>();
		for (var candidato : varredura.findCandidateComponents("dev.aurora")) {
			var tipo = ClassUtils.resolveClassName(candidato.getBeanClassName(),
					ConfiguracaoOpenApi.class.getClassLoader());
			if (porNome.putIfAbsent(tipo.getSimpleName(), tipo) != null) {
				repetidos.add(tipo.getSimpleName());
			}
		}
		// Nome ambíguo: não dá para saber a qual record o esquema se refere; fica como o
		// springdoc gerou.
		repetidos.forEach(porNome::remove);
		return porNome;
	}

}
