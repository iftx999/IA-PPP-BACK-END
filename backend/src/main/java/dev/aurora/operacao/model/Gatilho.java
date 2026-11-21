package dev.aurora.operacao.model;

/** O que abriu a sugestão de reotimização (J7; doc 10, 10.8). */
public enum Gatilho {

	/** OS emergencial aberta na área, ainda fora das rotas do dia. */
	EMERGENCIAL,

	/** OS paralisada há mais tempo que o limite (30 min por padrão). */
	PARALISACAO_LONGA,

	/** OS devolvida hoje por uma equipe que segue em jornada com outras OS. */
	DEVOLUCAO,

	/** Equipe com atraso estimado acima do limite (30 min por padrão). */
	ATRASO

}
