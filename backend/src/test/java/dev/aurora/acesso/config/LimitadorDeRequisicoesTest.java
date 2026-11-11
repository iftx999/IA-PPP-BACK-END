package dev.aurora.acesso.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LimitadorDeRequisicoesTest {

	private static final Instant INICIO = Instant.parse("2026-11-11T12:00:10Z");

	private final RelogioAjustavel relogio = new RelogioAjustavel(INICIO);

	@Test
	void recusaAcimaDoLimiteDoMinuto() {
		var limitador = new LimitadorDeRequisicoes(3, relogio);
		assertThat(IntStream.range(0, 5).mapToObj(i -> limitador.permitir("a"))).containsExactly(true, true, true,
				false, false);
	}

	@Test
	void cadaChaveTemSuaPropriaContagem() {
		var limitador = new LimitadorDeRequisicoes(1, relogio);
		assertThat(limitador.permitir("a")).isTrue();
		assertThat(limitador.permitir("b")).isTrue();
		assertThat(limitador.permitir("a")).isFalse();
	}

	@Test
	void novoMinutoLiberaEDescartaJanelasAntigas() {
		var limitador = new LimitadorDeRequisicoes(1, relogio);
		limitador.permitir("a");
		limitador.permitir("b");
		assertThat(limitador.permitir("a")).isFalse();

		relogio.avancar(Duration.ofSeconds(50));
		assertThat(limitador.permitir("a")).isTrue();
		assertThat(limitador.chavesEmMemoria()).isEqualTo(1);
	}

	@Test
	void retryAfterApontaParaAViradaDoMinuto() {
		var limitador = new LimitadorDeRequisicoes(1, relogio);
		assertThat(limitador.segundosAteLiberar()).isEqualTo(50);
		relogio.avancar(Duration.ofMillis(49_500));
		assertThat(limitador.segundosAteLiberar()).isEqualTo(1);
	}

	@Test
	void contagemCorretaComConcorrencia() throws Exception {
		var limitador = new LimitadorDeRequisicoes(500, relogio);
		var aceitas = new AtomicInteger();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			IntStream.range(0, 2000).forEach(i -> executor.submit(() -> {
				if (limitador.permitir("a")) {
					aceitas.incrementAndGet();
				}
			}));
		}
		assertThat(aceitas).hasValue(500);
	}

	@Test
	void limiteTemQueSerPositivo() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LimitadorDeRequisicoes(0, relogio));
	}

	private static final class RelogioAjustavel extends Clock {

		private Instant agora;

		RelogioAjustavel(Instant agora) {
			this.agora = agora;
		}

		void avancar(Duration tempo) {
			agora = agora.plus(tempo);
		}

		@Override
		public Instant instant() {
			return agora;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zona) {
			return this;
		}

	}

}
