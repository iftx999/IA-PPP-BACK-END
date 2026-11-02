package dev.aurora.campanhas.model;

/** Tipos de OS de ligação (I3). */
public enum TipoOrdem {

	VISTORIA, LIGACAO_NOVA, RELIGACAO, NOTIFICACAO;

	/** RN-19: só ligação nova e religação mudam a situação do imóvel na carga. */
	public boolean conferidaPelaCarga() {
		return this == LIGACAO_NOVA || this == RELIGACAO;
	}

}
