package dev.aurora.unidades.model;

import java.util.UUID;

/** Nó da hierarquia: Grupo > Concessão > Município > Setor comercial | ETE | EEE. */
public record Unidade(UUID id, String tipo, UUID unidadePaiId, String codigo, String nome, String codigoIbge) {
}
