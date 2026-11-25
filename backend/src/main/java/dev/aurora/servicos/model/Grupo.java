package dev.aurora.servicos.model;

import java.util.UUID;

/** Agrupador do catálogo (ex.: Ligações, Manutenção de rede). */
public record Grupo(UUID id, String codigo, String nome, boolean ativo, long servicos) {
}
