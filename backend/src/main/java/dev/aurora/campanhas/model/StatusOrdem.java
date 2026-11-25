package dev.aurora.campanhas.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo da OS (doc 10, 10.6; ADR 0044): planejada → programada (equipe e data) → em
 * execução ⇄ paralisada → encerrada; devolvida volta para a programação; cancelada sai
 * dela. Iniciar, paralisar e retomar vêm do app (M4).
 */
public enum StatusOrdem {

	PLANEJADA, PROGRAMADA, EM_EXECUCAO, PARALISADA, DEVOLVIDA, ENCERRADA, CANCELADA;

	/** Em aberto: ainda espera trabalho de campo ou programação. */
	public static final Set<StatusOrdem> ABERTAS = EnumSet.of(PLANEJADA, PROGRAMADA, EM_EXECUCAO, PARALISADA,
			DEVOLVIDA);

	/** Com uma equipe, numa data. */
	public static final Set<StatusOrdem> COM_EQUIPE = EnumSet.of(PROGRAMADA, EM_EXECUCAO, PARALISADA);

	public boolean aberta() {
		return ABERTAS.contains(this);
	}

	public boolean comEquipe() {
		return COM_EQUIPE.contains(this);
	}

	/** Programar ou reprogramar (equipe e data). */
	public boolean programavel() {
		return this == PLANEJADA || this == PROGRAMADA || this == DEVOLVIDA;
	}

	/** Cancelar: só antes de a equipe começar. */
	public boolean cancelavel() {
		return this == PLANEJADA || this == PROGRAMADA || this == DEVOLVIDA;
	}

	/** A equipe devolve para a programação. */
	public boolean devolvivel() {
		return COM_EQUIPE.contains(this);
	}

	/**
	 * Encerrar: em execução ou, no registro feito depois na web (sem o app), programada.
	 * Paralisada precisa ser retomada antes.
	 */
	public boolean encerravel() {
		return this == PROGRAMADA || this == EM_EXECUCAO;
	}

}
