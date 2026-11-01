package dev.aurora.contratos;

/** Como a multa de uma penalidade calculável é obtida (ADR 0035). */
public enum FormulaPenalidade {

	/** Percentual de uma base em reais por ponto percentual abaixo da meta. */
	PERCENTUAL_DA_BASE_POR_PONTO,

	/** Valor em reais por ponto percentual abaixo da meta. */
	VALOR_POR_PONTO,

	/** Valor em reais por período em que a meta não é atingida. */
	VALOR_FIXO

}
