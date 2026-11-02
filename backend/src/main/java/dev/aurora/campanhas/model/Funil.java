package dev.aurora.campanhas.model;

/**
 * Funil da campanha (I4). Planejadas = todas menos as canceladas; em campo = as que já
 * foram para uma equipe (com ou sem resultado).
 */
public record Funil(long total, long planejadas, long emCampo, long executadas, long confirmadas, long divergentes,
		long pendentesDeConferencia, long recusadas, long inviaveis, long reagendadas, long canceladas, long semEquipe,
		long atrasadas) {
}
