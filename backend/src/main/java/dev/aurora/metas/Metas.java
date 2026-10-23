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
	 * A5: meta de um aditivo aprovado. Metas de contrato da unidade que cruzam a vigência
	 * nova são encerradas na véspera (se começaram antes) ou substituídas por inteiro; a
	 * versão anterior vai para o histórico. Meta manual sobreposta impede (409).
	 */
	ResultadoAditivo aditarDoContrato(MetaDoContrato meta);

	/**
	 * Situação de cada unidade do escopo hoje (unidades sem meta incluídas, como
	 * SEM_META).
	 */
	List<SituacaoDaUnidade> situacoes();

}
