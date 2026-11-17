package dev.aurora.servicos.model;

import java.util.UUID;

/** Tipo de veículo exigido por serviços e usado nas rotas (RN-23). */
public record TipoVeiculo(UUID id, String codigo, String nome, PerfilRota perfilRota, boolean ativo) {
}
