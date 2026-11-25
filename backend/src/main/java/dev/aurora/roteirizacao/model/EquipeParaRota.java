package dev.aurora.roteirizacao.model;

import java.time.LocalTime;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Equipe como a roteirização usa (ADR 0046).
 *
 * @param ultimaPosicao última posição do agente na jornada aberta (RN-27)
 */
public record EquipeParaRota(UUID id, String nome, boolean ativa, @Nullable Coordenada base, LocalTime jornadaInicio,
		LocalTime jornadaFim, int pausaMin, PerfilRota perfil, @Nullable Coordenada ultimaPosicao) {
}
