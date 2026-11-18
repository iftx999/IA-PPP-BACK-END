package dev.aurora.campanhas.model;

/**
 * O que o servidor fez com um evento do app (RN-26; 10.9.4 e 10.9.5).
 */
public enum SituacaoEvento {

	/** Aplicado à OS. */
	APLICADO,
	/** Já recebido antes (mesmo id): nada muda; a resposta traz a situação original. */
	REPETIDO,
	/**
	 * Registrado, mas a programação mudou (cancelada, outra equipe, já encerrada): vale o
	 * campo e o programador decide (J11).
	 */
	CONFLITO,
	/** Não aplicado: a programação mudou e o campo ainda não tinha trabalhado na OS. */
	SUPERADO,
	/** Espera o evento anterior da mesma OS (sequência fora de ordem). */
	AGUARDANDO,
	/** Dados inválidos ou ação impossível na situação da OS. */
	REJEITADO;

	/** Ocupa a sequência da OS (o próximo evento pode ser processado). */
	public boolean processado() {
		return this != AGUARDANDO;
	}

}
