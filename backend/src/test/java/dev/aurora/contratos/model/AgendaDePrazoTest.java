package dev.aurora.contratos.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.aurora.contratos.Recorrencia;
import dev.aurora.contratos.model.AgendaDePrazo.Cumprimento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgendaDePrazoTest {

	private static final LocalDate PRIMEIRO = LocalDate.of(2026, 1, 31);

	@Test
	void vencimentoUnicoTerminaAoSerCumprido() {
		assertThat(agenda(null, null).proximoVencimento()).contains(PRIMEIRO);
		assertThat(agenda(null, null, PRIMEIRO).proximoVencimento()).isEmpty();
		// Data mudada depois de cumprir: o novo vencimento volta a valer.
		assertThat(agenda(null, null, PRIMEIRO.minusMonths(2)).proximoVencimento()).contains(PRIMEIRO);
	}

	@Test
	void mensalSegueOPrimeiroVencimentoAjustandoOFimDoMes() {
		assertThat(agenda(Recorrencia.MENSAL, null, PRIMEIRO).proximoVencimento()).contains(LocalDate.of(2026, 2, 28));
		// O dia volta a 31 quando o mês tem: a conta parte sempre do primeiro vencimento.
		assertThat(agenda(Recorrencia.MENSAL, null, PRIMEIRO, LocalDate.of(2026, 2, 28)).proximoVencimento())
			.contains(LocalDate.of(2026, 3, 31));
	}

	@Test
	void cumprimentosForaDeOrdemUsamOUltimoVencimento() {
		var agenda = agenda(Recorrencia.TRIMESTRAL, null, LocalDate.of(2026, 7, 31), PRIMEIRO);
		assertThat(agenda.proximoVencimento()).contains(LocalDate.of(2026, 10, 31));
		assertThat(agenda.ultimoCumprimento()).map(Cumprimento::vencimento).contains(LocalDate.of(2026, 7, 31));
	}

	@Test
	void repeticoesNaoPassamDoFimDoContrato() {
		var fim = LocalDate.of(2026, 12, 31);
		assertThat(agenda(Recorrencia.SEMESTRAL, fim, PRIMEIRO).proximoVencimento())
			.contains(LocalDate.of(2026, 7, 31));
		assertThat(agenda(Recorrencia.SEMESTRAL, fim, PRIMEIRO, LocalDate.of(2026, 7, 31)).proximoVencimento())
			.isEmpty();
		// O primeiro vencimento vale mesmo depois do fim (foi informado assim).
		assertThat(agenda(Recorrencia.ANUAL, PRIMEIRO.minusDays(1)).proximoVencimento()).contains(PRIMEIRO);
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			mensal,              MENSAL
			Mensalmente,         MENSAL
			trimestral,          TRIMESTRAL
			trimestrais,         TRIMESTRAL
			Bimestral,           BIMESTRAL
			semestral,           SEMESTRAL
			anual,               ANUAL
			anuais,              ANUAL
			quadrienal,          QUADRIENAL
			a cada 4 anos,       QUADRIENAL
			A cada 6 meses,      SEMESTRAL
			quinquenal,          QUINQUENAL
			""")
	void sugereARecorrenciaDoTextoDaPeriodicidade(String texto, Recorrencia esperada) {
		assertThat(Recorrencia.sugerir(texto)).contains(esperada);
	}

	@ParameterizedTest
	@CsvSource({ "diária", "semanal", "a cada 7 meses", "única", "''" })
	void frequenciasQueNaoSaoVencimentosNaoTemSugestao(String texto) {
		assertThat(Recorrencia.sugerir(texto)).isEmpty();
	}

	private static AgendaDePrazo agenda(Recorrencia recorrencia, LocalDate fim, LocalDate... cumpridos) {
		return new AgendaDePrazo(PRIMEIRO, recorrencia,
				List.of(cumpridos).stream().map(d -> new Cumprimento(d, Instant.EPOCH, "Ana")).toList(), fim);
	}

}
