package dev.aurora.integracoes.model;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumo de uma carga: o que foi lido, gravado, recusado e quantas medições foram
 * recalculadas.
 */
public record ResultadoCarga(UUID execucaoId, YearMonth competencia, long lidos, long novos, long atualizados,
		long inalterados, long rejeitados, List<Rejeicao> rejeicoes, int medicoes) {

	public long gravados() {
		return novos + atualizados;
	}

}
