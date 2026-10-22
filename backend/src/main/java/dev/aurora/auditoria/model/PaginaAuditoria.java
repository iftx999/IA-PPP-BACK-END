package dev.aurora.auditoria.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Página da consulta; {@code proximo} é o cursor da página seguinte (nulo no fim). */
public record PaginaAuditoria(List<EventoConsultado> eventos, @Nullable String proximo) {
}
