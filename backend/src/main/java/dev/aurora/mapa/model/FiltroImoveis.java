package dev.aurora.mapa.model;

import java.time.YearMonth;
import java.util.Set;

/**
 * Imóveis que o mapa mostra: situações ligadas e se entram os de localização aproximada.
 */
public record FiltroImoveis(YearMonth competencia, Set<SituacaoNoMapa> situacoes, boolean aproximadas) {

	public FiltroImoveis {
		situacoes = Set.copyOf(situacoes);
	}

	public String[] situacoesNoBanco() {
		return situacoes.stream().map(SituacaoNoMapa::noBanco).sorted().toArray(String[]::new);
	}

}
