package dev.aurora.alertas.model;

/** Quais alertas novos a pessoa recebe por e-mail (ADR 0024). Padrão: só os críticos. */
public enum PreferenciaEmail {

	NENHUM, CRITICOS, TODOS;

	public boolean recebe(Severidade severidade) {
		return this == TODOS || (this == CRITICOS && severidade == Severidade.CRITICO);
	}

}
