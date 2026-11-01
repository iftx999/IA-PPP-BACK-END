package dev.aurora.contratos;

/**
 * Quando a multa é aferida, a partir do prazo da meta (ADR 0035): todo mês ou a cada 12
 * meses.
 */
public enum PeriodicidadePenalidade {

	MENSAL(1), ANUAL(12);

	private final int meses;

	PeriodicidadePenalidade(int meses) {
		this.meses = meses;
	}

	public int meses() {
		return meses;
	}

}
