package dev.aurora.integracoes.model;

/** Como os dados chegam (schema herdado). */
public enum TipoAdaptador {

	/**
	 * Agente Aurora instalado na rede da concessionária (envia matrícula e ponto, RN-09).
	 */
	AGENTE,

	/** Arquivo enviado pela tela. */
	ARQUIVO,

	CDC, API, ETL_JDBC

}
