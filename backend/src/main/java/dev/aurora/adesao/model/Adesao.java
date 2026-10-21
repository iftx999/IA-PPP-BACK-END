package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * RN-01 e RN-02. Adesão = ligados / imóveis com rede disponível × 100, onde "com rede" = ligados + factíveis +
 * cortados (imóveis sem rede ficam fora). Em níveis superiores soma-se ligados e com rede (não se faz média de
 * percentuais).
 */
public record Adesao(long ligados, long comRede) {

	public Adesao {
		if (ligados < 0 || comRede < 0 || ligados > comRede) {
			throw new IllegalArgumentException("ligados=%d, comRede=%d".formatted(ligados, comRede));
		}
	}

	/** Percentual com 4 casas; nulo quando não há imóvel com rede. */
	public BigDecimal percentual() {
		if (comRede == 0) {
			return null;
		}
		return BigDecimal.valueOf(ligados * 100L).divide(BigDecimal.valueOf(comRede), 4, RoundingMode.HALF_UP);
	}

	public long semLigacao() {
		return comRede - ligados;
	}

	/** RN-04: variação em pontos percentuais em relação a outra competência (nula se faltar algum lado). */
	public BigDecimal variacaoPp(Adesao anterior) {
		if (anterior == null || percentual() == null || anterior.percentual() == null) {
			return null;
		}
		return percentual().subtract(anterior.percentual());
	}

}
