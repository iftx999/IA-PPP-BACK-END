package dev.aurora.campanhas.model;

import java.util.UUID;

/** Produtividade de uma equipe na campanha (I4). */
public record Produtividade(UUID equipeId, String equipeNome, TipoEquipe tipo, long emCampo, long executadas,
		long confirmadas, long recusadas, long inviaveis, long atrasadas) {
}
