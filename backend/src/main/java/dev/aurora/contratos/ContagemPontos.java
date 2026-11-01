package dev.aurora.contratos;

/** Como os pontos percentuais abaixo da meta entram na fórmula (ADR 0035). */
public enum ContagemPontos {

	/** A fração conta pelo que vale (2,4 pontos = 2,4). */
	PROPORCIONAL,

	/** "Por ponto percentual ou fração": a fração conta como ponto inteiro (2,4 = 3). */
	POR_PONTO_OU_FRACAO

}
