package dev.aurora.servicos.model;

/**
 * Tipo do campo que a equipe preenche ao encerrar a OS (J2). Mínimo e máximo valem para o
 * valor (inteiro, decimal), para a quantidade de caracteres (texto) ou de fotos (foto).
 */
public enum TipoParametro {

	INTEIRO, DECIMAL, TEXTO, LISTA, LISTA_MULTIPLA, SIM_NAO, DATA_HORA, FOTO;

	public boolean temOpcoes() {
		return this == LISTA || this == LISTA_MULTIPLA;
	}

	public boolean numerico() {
		return this == INTEIRO || this == DECIMAL;
	}

	/** Aceita mínimo e máximo. */
	public boolean temFaixa() {
		return numerico() || this == TEXTO || this == FOTO;
	}

	/** Pode ser a origem de uma condição de outro parâmetro. */
	public boolean condiciona() {
		return this == SIM_NAO || this == LISTA;
	}

}
