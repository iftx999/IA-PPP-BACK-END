package dev.aurora.unidades.model;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** Campos editáveis de uma unidade (o tipo não muda depois de criada). */
public record DadosUnidade(String codigo, String nome, @Nullable String codigoIbge, @Nullable BigDecimal latitude,
		@Nullable BigDecimal longitude) {
}
