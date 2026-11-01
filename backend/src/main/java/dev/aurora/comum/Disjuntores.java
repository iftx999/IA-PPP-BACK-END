package dev.aurora.comum;

import java.time.Duration;
import java.util.function.Predicate;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Disjuntores dos serviços externos (ADR 0030), com estado e contagens no Micrometer
 * ({@code resilience4j.circuitbreaker.*}, painel e alertas do Grafana). Mudança de estado
 * vai para o log.
 */
@Component
public class Disjuntores {

	/** Provedor de IA (assistente e extração de contratos usam o mesmo). */
	public static final String IA = "ia";

	/** Provedor de identidade (Keycloak Admin API). */
	public static final String IDENTIDADE = "identidade";

	private static final Logger LOG = LoggerFactory.getLogger(Disjuntores.class);

	private final CircuitBreakerRegistry registro = CircuitBreakerRegistry.ofDefaults();

	private final Duration espera;

	Disjuntores(MeterRegistry metricas, @Value("${aurora.disjuntor.espera:PT30S}") Duration espera) {
		this.espera = espera;
		TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registro).bindTo(metricas);
	}

	/**
	 * O mesmo disjuntor para o mesmo nome; {@code ignorar} vale na primeira criação.
	 * @param ignorar erros que não indicam falha do serviço (ex.: pedido inválido)
	 */
	public Disjuntor de(String nome, Predicate<Throwable> ignorar) {
		var existente = registro.find(nome);
		if (existente.isPresent()) {
			return new Disjuntor(existente.get());
		}
		var novo = registro.circuitBreaker(nome, Disjuntor.configuracao(espera, ignorar));
		novo.getEventPublisher().onStateTransition(e -> LOG.warn("Disjuntor {}: {}", nome, e.getStateTransition()));
		return new Disjuntor(novo);
	}

}
