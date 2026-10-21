package dev.aurora.imoveis.model;

/** Situações "sem ligação" (RN-02): rede disponível e sem ligação ativa. */
public enum SituacaoImovel {

	/** Nunca se ligou, ou não tem ligação ativa. */
	FACTIVEL,

	/** Teve ligação e foi cortado. */
	CORTADO

}
