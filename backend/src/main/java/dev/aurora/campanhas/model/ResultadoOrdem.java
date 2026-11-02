package dev.aurora.campanhas.model;

/** Resultado de campo de uma OS (I3). */
public enum ResultadoOrdem {

	EXECUTADA, RECUSADA, REAGENDADA, INVIAVEL;

	public StatusOrdem status() {
		return StatusOrdem.valueOf(name());
	}

	public boolean exigeMotivo() {
		return this == RECUSADA || this == INVIAVEL;
	}

}
