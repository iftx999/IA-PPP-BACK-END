package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.ServicoDaOrdem;
import dev.aurora.servicos.model.Calendario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-21 (prazo), J5 (quantidade do trecho) e instruções da OS (ADR 0044). */
class RegrasDaProgramacaoTest {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	/** Quarta-feira, 18/11/2026, 10h; sexta 20/11 é a Consciência Negra. */
	private static final Instant AGORA = Instant.parse("2026-11-18T13:00:00Z");

	private static final ServicoDaOrdem LIMPEZA = servico("M");

	@Test
	void emergencialEmHorasCorridas() {
		assertThat(prazo(Prioridade.EMERGENCIAL, Set.of())).isEqualTo(AGORA.plusSeconds(6 * 3600));
	}

	@Test
	void demaisNoFimDoDiaUtil() {
		// Alta = 2 dias úteis: quinta 19 e (sexta 20 é feriado nacional) segunda 23.
		assertThat(prazo(Prioridade.ALTA, Set.of())).isEqualTo(Instant.parse("2026-11-24T02:59:59Z"));
		// Feriado municipal na segunda empurra para terça.
		assertThat(prazo(Prioridade.ALTA, Set.of(LocalDate.of(2026, 11, 23))))
			.isEqualTo(Instant.parse("2026-11-25T02:59:59Z"));
		// Média = 5 dias úteis: 19, 23, 24, 25, 26.
		assertThat(prazo(Prioridade.MEDIA, Set.of())).isEqualTo(Instant.parse("2026-11-27T02:59:59Z"));
	}

	@Test
	void quantidadeDoTrechoEmMetros() {
		assertThat(ProgramacaoService.quantidadeDoTrecho(LIMPEZA, new BigDecimal("123.4")))
			.isEqualByComparingTo("123.40");
		assertThat(ProgramacaoService.quantidadeDoTrecho(LIMPEZA, BigDecimal.ZERO)).isEqualByComparingTo("0.01");
		assertThat(ProgramacaoService.quantidadeDoTrecho(servico("UN"), new BigDecimal("80")))
			.isEqualByComparingTo("1");
	}

	@Test
	void instrucoes() {
		assertThat(ProgramacaoService.instrucoes("  ")).isNull();
		assertThat(ProgramacaoService.instrucoes(" Portão azul ")).isEqualTo("Portão azul");
		assertThatThrownBy(() -> ProgramacaoService.instrucoes("x".repeat(501)))
			.isInstanceOf(RegraVioladaException.class);
	}

	private static Instant prazo(Prioridade prioridade, Set<LocalDate> feriados) {
		return ProgramacaoService.prazo(LIMPEZA, prioridade, AGORA, FUSO,
				(inicio, dias) -> Calendario.somarDiasUteis(inicio, dias, feriados::contains));
	}

	private static ServicoDaOrdem servico(String unidade) {
		return new ServicoDaOrdem(UUID.randomUUID(), UUID.randomUUID(), 1, "LIMPEZA_REDE", "Limpeza de rede", "Rede",
				"TRECHO", unidade, false, true, 6, 2, 5, 10);
	}

}
