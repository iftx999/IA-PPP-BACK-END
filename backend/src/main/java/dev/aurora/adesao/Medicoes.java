package dev.aurora.adesao;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** API do módulo de adesão para os demais módulos. */
public interface Medicoes {

	/**
	 * Recalcula a adesão da competência em todas as unidades do escopo (RN-01): ligados ÷
	 * (ligados + factíveis + cortados) da subárvore de cada unidade, a partir da situação
	 * mensal dos imóveis. Unidades sem imóvel com rede ficam sem medição.
	 * @return quantas unidades tiveram medição gravada
	 */
	int recalcular(YearMonth competencia);

	/**
	 * Adesão e semáforo de cada unidade pedida (ex.: municípios no mapa), na ordem
	 * pedida. Unidade fora do escopo ou sem medição vem sem percentual e
	 * {@link Situacao#SEM_META}.
	 */
	List<AdesaoDaUnidade> situacaoDas(Collection<UUID> unidades, YearMonth competencia);

}
