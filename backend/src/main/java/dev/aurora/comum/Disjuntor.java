package dev.aurora.comum;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Disjuntor (circuit breaker) de um serviço externo (ADR 0030): depois de muitas falhas
 * seguidas, as chamadas param por um tempo e falham na hora, em vez de cada pedido
 * esperar o tempo máximo. Erros causados pelo próprio pedido (ex.: 400, 404, 409) não
 * contam.
 */
public final class Disjuntor {

	/** Pelo menos 10 chamadas na janela das últimas 20; abre com metade falhando. */
	static CircuitBreakerConfig configuracao(Duration espera, Predicate<Throwable> ignorar) {
		return CircuitBreakerConfig.custom()
			.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
			.slidingWindowSize(20)
			.minimumNumberOfCalls(10)
			.failureRateThreshold(50)
			.waitDurationInOpenState(espera)
			.permittedNumberOfCallsInHalfOpenState(3)
			.automaticTransitionFromOpenToHalfOpenEnabled(true)
			.ignoreException(ignorar)
			.build();
	}

	/**
	 * Erros de IA causados pelo próprio pedido (os dois SDKs usam esses nomes): não
	 * indicam falha do provedor.
	 */
	public static final Predicate<Throwable> ERRO_DO_PEDIDO_DE_IA = erro -> {
		var nome = erro.getClass().getSimpleName();
		return nome.equals("BadRequestException") || nome.equals("UnprocessableEntityException");
	};

	private final CircuitBreaker disjuntor;

	Disjuntor(CircuitBreaker disjuntor) {
		this.disjuntor = disjuntor;
	}

	/** Disjuntor isolado, sem métricas (testes de unidade e clientes criados à mão). */
	public static Disjuntor isolado(String nome, Predicate<Throwable> ignorar) {
		return new Disjuntor(CircuitBreaker.of(nome, configuracao(Duration.ofSeconds(30), ignorar)));
	}

	/**
	 * @param quandoAberto erro a lançar sem chamar o serviço, enquanto o disjuntor
	 * estiver aberto
	 */
	public <T> T executar(Supplier<T> chamada, Supplier<? extends RuntimeException> quandoAberto) {
		try {
			return disjuntor.executeSupplier(chamada);
		}
		catch (CallNotPermittedException aberto) {
			throw quandoAberto.get();
		}
	}

	public String nome() {
		return disjuntor.getName();
	}

	public CircuitBreaker.State estado() {
		return disjuntor.getState();
	}

}
