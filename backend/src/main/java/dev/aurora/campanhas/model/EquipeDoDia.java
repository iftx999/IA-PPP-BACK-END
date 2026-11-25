package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Equipe da pessoa como o app mostra antes de iniciar a jornada (J8).
 *
 * @param ordensHoje OS programadas para hoje ainda abertas
 * @param jornadaAberta alguém da equipe já iniciou a jornada
 */
public record EquipeDoDia(UUID id, String nome, long ordensHoje, long emergenciais, @Nullable String veiculoNome,
		boolean jornadaAberta) {
}
