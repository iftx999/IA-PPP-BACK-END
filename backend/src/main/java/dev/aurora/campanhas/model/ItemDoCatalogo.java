package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Tipo de veículo ou habilidade do catálogo, para o cadastro da equipe.
 *
 * @param perfilRota MOTO, CARRO ou CAMINHAO; só nos veículos
 */
public record ItemDoCatalogo(UUID id, String codigo, String nome, boolean ativo, @Nullable String perfilRota) {
}
