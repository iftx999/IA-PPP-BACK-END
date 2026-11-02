package dev.aurora.campanhas.model;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/** Resultado de campo informado pela equipe ou pelo gestor (I3). */
public record RegistroResultado(ResultadoOrdem resultado, @Nullable MotivoResultado motivo, @Nullable String observacao,
		@Nullable LocalDate reagendadaPara) {
}
