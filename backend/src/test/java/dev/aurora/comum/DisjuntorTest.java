package dev.aurora.comum;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisjuntorTest {

	static final class ErroDoPedido extends RuntimeException {

	}

	@Test
	void abreDepoisDeMuitasFalhasEPassaAFalharSemChamar() {
		var disjuntor = Disjuntor.isolado("teste", e -> e instanceof ErroDoPedido);
		var chamadas = new AtomicInteger();
		for (int i = 0; i < 10; i++) {
			assertThatThrownBy(() -> disjuntor.executar(() -> {
				chamadas.incrementAndGet();
				throw new IllegalStateException("fora do ar");
			}, () -> new IllegalArgumentException("aberto"))).isInstanceOf(IllegalStateException.class);
		}
		assertThat(disjuntor.estado()).isEqualTo(CircuitBreaker.State.OPEN);

		assertThatThrownBy(() -> disjuntor.executar(() -> chamadas.incrementAndGet(),
				() -> new IllegalArgumentException("aberto")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("aberto");
		assertThat(chamadas).hasValue(10);
	}

	@Test
	void erroDoProprioPedidoNaoAbre() {
		var disjuntor = Disjuntor.isolado("teste", e -> e instanceof ErroDoPedido);
		for (int i = 0; i < 20; i++) {
			assertThatThrownBy(() -> disjuntor.executar(() -> {
				throw new ErroDoPedido();
			}, IllegalArgumentException::new)).isInstanceOf(ErroDoPedido.class);
		}
		assertThat(disjuntor.estado()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(disjuntor.executar(() -> "ok", IllegalArgumentException::new)).isEqualTo("ok");
	}

	@Test
	void reconheceErrosDePedidoDosSdksDeIa() {
		class BadRequestException extends RuntimeException {

		}
		class InternalServerException extends RuntimeException {

		}
		assertThat(Disjuntor.ERRO_DO_PEDIDO_DE_IA.test(new BadRequestException())).isTrue();
		assertThat(Disjuntor.ERRO_DO_PEDIDO_DE_IA.test(new InternalServerException())).isFalse();
	}

}
