package dev.aurora.mapa.model;

/**
 * Precisão da localização (E6, risco do geocódigo CNEFE no doc 06). Só {@code ENDERECO} é
 * exata; as demais aparecem como "localização aproximada".
 */
public enum Precisao {

	ENDERECO, LOGRADOURO, BAIRRO, MANUAL;

	public boolean aproximada() {
		return this != ENDERECO;
	}

}
