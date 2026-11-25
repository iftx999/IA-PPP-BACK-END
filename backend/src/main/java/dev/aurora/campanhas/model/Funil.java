package dev.aurora.campanhas.model;

/**
 * Funil da campanha (I4, ADR 0044). Planejadas = todas menos as canceladas; em campo = as
 * que já foram para uma equipe (com ou sem resultado); sem equipe = planejadas e
 * devolvidas.
 */
public record Funil(long total, long planejadas, long emCampo, long executadas, long confirmadas, long divergentes,
		long pendentesDeConferencia, long naoExecutadas, long devolvidas, long canceladas, long semEquipe,
		long atrasadas) {
}
