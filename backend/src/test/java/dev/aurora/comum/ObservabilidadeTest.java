package dev.aurora.comum;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.aurora.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0022: com porta de gerenciamento separada, saúde e métricas ficam nela sem token, e
 * não existem na porta pública.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "management.server.port=0", "aurora.tarefas.habilitadas=false" })
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class ObservabilidadeTest {

	private final HttpClient http = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	int portaPublica;

	@Value("${local.management.port}")
	int portaGerenciamento;

	@Test
	void metricasESaudeSoNaPortaDeGerenciamento() throws Exception {
		assertThat(portaGerenciamento).isNotEqualTo(portaPublica);

		var saude = get(portaGerenciamento, "/actuator/health/readiness");
		assertThat(saude.statusCode()).isEqualTo(200);
		var metricas = get(portaGerenciamento, "/actuator/prometheus");
		assertThat(metricas.statusCode()).isEqualTo(200);
		assertThat(metricas.body()).contains("aplicacao=\"aurora-api\"").contains("jvm_memory_used_bytes");
		var sbom = get(portaGerenciamento, "/actuator/sbom/application");
		assertThat(sbom.statusCode()).isEqualTo(200);
		assertThat(sbom.body()).contains("\"bomFormat\"").contains("spring-boot");
		// Endpoints não liberados continuam fechados mesmo na porta interna.
		assertThat(get(portaGerenciamento, "/actuator/env").statusCode()).isIn(401, 403, 404);

		assertThat(get(portaPublica, "/actuator/prometheus").statusCode()).isIn(401, 404);
	}

	private HttpResponse<String> get(int porta, String caminho) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

}
