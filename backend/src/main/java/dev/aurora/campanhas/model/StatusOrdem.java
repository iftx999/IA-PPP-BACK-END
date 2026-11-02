package dev.aurora.campanhas.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fluxo da OS (ADR 0036): planejada → em campo (distribuída a uma equipe) → resultado.
 * Reagendada continua em campo, com nova data.
 */
public enum StatusOrdem {

	PLANEJADA, EM_CAMPO, REAGENDADA, EXECUTADA, RECUSADA, INVIAVEL, CANCELADA;

	/** Em aberto: ainda espera trabalho de campo. */
	public static final Set<StatusOrdem> ABERTAS = EnumSet.of(PLANEJADA, EM_CAMPO, REAGENDADA);

	public boolean aberta() {
		return ABERTAS.contains(this);
	}

	/** Com a equipe, esperando o resultado. */
	public boolean emCampo() {
		return this == EM_CAMPO || this == REAGENDADA;
	}

}
