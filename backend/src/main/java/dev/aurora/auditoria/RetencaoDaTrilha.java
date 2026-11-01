package dev.aurora.auditoria;

/**
 * Retenção da trilha (ADR 0029): prazo do tenant (política do cliente, alterada pela
 * gestão de acesso), expurgo do que passou dele e partições anuais criadas com
 * antecedência (agenda de operação).
 */
public interface RetencaoDaTrilha {

	/** Prazo do tenant do contexto (exige escopo no tenant inteiro). */
	PrazoDeRetencao consultar();

	/**
	 * Novo prazo, entre o mínimo e o máximo. Reduzir vale a partir do próximo expurgo
	 * diário. Registrado na trilha.
	 */
	PrazoDeRetencao alterar(int meses);

	/**
	 * Apaga, no tenant do contexto, os eventos anteriores ao prazo de retenção e registra
	 * o expurgo na própria trilha.
	 * @return quantos eventos foram apagados
	 */
	long expurgarVencidos();

	/**
	 * Cria as partições anuais da trilha do ano atual e dos próximos (sem tenant).
	 * @return quantas foram criadas agora
	 */
	int prepararParticoes();

}
