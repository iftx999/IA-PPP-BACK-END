package dev.aurora.imoveis.model;

/** Situações "sem ligação" (RN-02): rede disponível e sem ligação ativa. */
public enum SituacaoImovel {

	/** Rede pública disponível na rua, mas o imóvel ainda não fez a conexão física. */
	FACTIVEL,

	/**
	 * Tem conexão física, com o serviço interrompido: corte por falta de pagamento ou
	 * suspensão a pedido do cliente.
	 */
	CORTADO

}
