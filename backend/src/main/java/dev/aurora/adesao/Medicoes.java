package dev.aurora.adesao;

import java.time.YearMonth;

/** API do módulo de adesão para os demais módulos. */
public interface Medicoes {

	/**
	 * Recalcula a adesão da competência em todas as unidades do escopo (RN-01): ligados ÷
	 * (ligados + factíveis + cortados) da subárvore de cada unidade, a partir da situação
	 * mensal dos imóveis. Unidades sem imóvel com rede ficam sem medição.
	 * @return quantas unidades tiveram medição gravada
	 */
	int recalcular(YearMonth competencia);

}
