package dev.aurora.servicos;

/** API do catálogo de serviços para os demais módulos. */
public interface Servicos {

	/**
	 * Semeia o catálogo inicial do cliente atual (doc 10, 10.5), se ele ainda não tem
	 * nenhum grupo. Chamado no provisionamento.
	 */
	void semearCatalogoInicial();

}
