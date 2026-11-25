package dev.aurora.campanhas.model;

/** Conflito entre o campo e a programação (doc 10, 10.9.5). */
public enum TipoConflito {

	/** A OS foi cancelada e o campo trabalhou nela. */
	OS_CANCELADA,
	/** A OS foi passada para outra equipe e esta trabalhou nela. */
	OUTRA_EQUIPE,
	/** A OS já estava encerrada (por outra equipe ou pelo gestor). */
	JA_ENCERRADA

}
