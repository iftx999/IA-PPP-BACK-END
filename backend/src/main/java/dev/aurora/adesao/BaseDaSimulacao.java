package dev.aurora.adesao;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Ponto de partida do simulador de penalidades (ADR 0035): a série recente da unidade, o
 * ritmo atual (RN-05) e a meta aplicável (RN-06) na competência mais recente.
 *
 * @param serie até 12 meses, em ordem cronológica, terminando na competência atual
 * @param ritmoAtual novas ligações por mês; nulo quando não há meses comparáveis
 * @param meta nula quando a unidade não tem meta aplicável
 */
public record BaseDaSimulacao(UUID unidadeId, String unidadeNome, String tipo, YearMonth competencia,
		List<MesMedido> serie, @Nullable BigDecimal ritmoAtual, @Nullable MetaDaSimulacao meta) {

	public MesMedido atual() {
		return serie.getLast();
	}

	public record MesMedido(YearMonth competencia, long ligados, long comRede) {
	}

	/**
	 * @param prazo fim da vigência; nulo = meta sem prazo (não há "abaixo no prazo")
	 * @param herdada a meta vem de uma unidade acima ({@code unidadeNome})
	 */
	public record MetaDaSimulacao(BigDecimal valor, @Nullable YearMonth prazo, UUID unidadeId, String unidadeNome,
			boolean herdada, @Nullable String origem) {
	}

}
