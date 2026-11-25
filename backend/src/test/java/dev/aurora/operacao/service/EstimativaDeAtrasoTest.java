package dev.aurora.operacao.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.aurora.operacao.model.ParadaPrevista;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstimativaDeAtrasoTest {

	private static final Instant OITO = Instant.parse("2026-11-21T11:00:00Z");

	@Test
	void aCaminhoDaPrimeiraParadaNoHorario() {
		var rota = List.of(parada(1, 30, 60, 30, "PROGRAMADA", null, null));
		// Jornada às 8h, 30 min de deslocamento: chega 8h30, como previsto.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(10)))).isZero();
	}

	@Test
	void aCaminhoEAtrasadaPeloRelogio() {
		var rota = List.of(parada(1, 30, 60, 30, "PROGRAMADA", null, null));
		// 9h e ainda não chegou na parada prevista para 8h30.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(60)))).isEqualTo(30);
	}

	@Test
	void usaOFimRealDaParadaAnteriorMaisODeslocamento() {
		var rota = List.of(parada(1, 30, 60, 30, "ENCERRADA", min(30), min(100)),
				parada(2, 80, 120, 20, "PROGRAMADA", null, null));
		// Encerrou a 1ª às 9h40 (previsto 9h); +20 min: chega 10h, previsto 9h20.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(100)))).isEqualTo(40);
	}

	@Test
	void emExecucaoProjetaOTermino() {
		var rota = List.of(parada(1, 30, 90, 30, "EM_EXECUCAO", min(50), null));
		// Iniciou 8h50 (previsto 8h30), 60 min de serviço: termina 9h50, previsto 9h30.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(55)))).isEqualTo(20);
		// Já passou do término projetado: conta até agora.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(150)))).isEqualTo(60);
	}

	@Test
	void adiantadaDaNegativo() {
		var rota = List.of(parada(1, 30, 60, 30, "ENCERRADA", min(5), min(20)),
				parada(2, 90, 120, 10, "PROGRAMADA", null, null));
		// Encerrou 8h20, +10 min: chega 8h30, previsto 9h30.
		assertThat(EstimativaDeAtraso.minutos(rota, OITO, OITO.plus(min(20)))).isEqualTo(-60);
	}

	@Test
	void rotaTodaFeitaEOsQueSairamDaRota() {
		var feita = List.of(parada(1, 30, 60, 30, "ENCERRADA", min(30), min(60)),
				parada(2, 70, 90, 10, "DEVOLVIDA", null, min(65)));
		assertThat(EstimativaDeAtraso.minutos(feita, OITO, OITO.plus(min(300)))).isZero();
		// Programada, mas passou para outra equipe (fim informado): pula, sem contar como
		// fim
		// da equipe. Início da jornada (8h) + 10 min já passou: chega agora (8h15),
		// previsto 9h10.
		var movida = List.of(parada(1, 30, 60, 30, "PROGRAMADA", null, min(10)),
				parada(2, 70, 90, 10, "PROGRAMADA", null, null));
		assertThat(EstimativaDeAtraso.minutos(movida, OITO, OITO.plus(min(15)))).isEqualTo(-55);
	}

	private static Duration min(long m) {
		return Duration.ofMinutes(m);
	}

	private static ParadaPrevista parada(int ordem, long chegada, long saida, int ate, String status,
			@Nullable Duration iniciada, @Nullable Duration fim) {
		return new ParadaPrevista(UUID.randomUUID(), ordem, OITO.plus(min(chegada)), OITO.plus(min(saida)), ate, status,
				iniciada == null ? null : OITO.plus(iniciada), fim == null ? null : OITO.plus(fim));
	}

}
