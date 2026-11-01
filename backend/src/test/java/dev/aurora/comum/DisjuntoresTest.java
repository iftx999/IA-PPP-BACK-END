package dev.aurora.comum;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisjuntoresTest {

	@Test
	void mesmoNomeMesmoDisjuntorComEstadoNasMetricas() {
		var metricas = new SimpleMeterRegistry();
		var disjuntores = new Disjuntores(metricas, Duration.ofSeconds(30));

		var ia = disjuntores.de(Disjuntores.IA, e -> false);
		assertThat(disjuntores.de(Disjuntores.IA, e -> true).nome()).isEqualTo(ia.nome());

		// O alerta DisjuntorAberto (infra/producao/observabilidade/alertas.yml) usa esta
		// métrica.
		var aberto = metricas.find("resilience4j.circuitbreaker.state")
			.tags("name", Disjuntores.IA, "state", "open")
			.gauge();
		assertThat(aberto).isNotNull();
		assertThat(aberto.value()).isZero();
	}

}
