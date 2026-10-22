package dev.aurora.metas;

import java.util.List;
import java.util.UUID;

/** API do módulo de metas para os demais módulos. */
public interface Metas {

	/**
	 * Cria a meta de adesão aprovada a partir de um item contratual (RN-06, RN-10). Valem
	 * as mesmas regras da meta manual (escopo, unidade operacional, vigências sem
	 * sobreposição) e o evento META_DEFINIDA.
	 * @return id da meta criada
	 */
	UUID definirDoContrato(MetaDoContrato meta);

	/**
	 * Situação de cada unidade do escopo hoje (unidades sem meta incluídas, como
	 * SEM_META).
	 */
	List<SituacaoDaUnidade> situacoes();

}
