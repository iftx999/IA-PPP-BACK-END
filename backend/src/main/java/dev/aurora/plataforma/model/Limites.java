package dev.aurora.plataforma.model;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * Limites contratados do cliente; nulo = sem limite.
 *
 * @param usuarios usuários ativos (convites pendentes contam)
 * @param assistenteTokensPorMes tokens do assistente por mês (ADR 0018)
 * @param assistenteUsdPorMes custo do assistente por mês, em dólares
 */
public record Limites(@Nullable Integer usuarios, @Nullable Long assistenteTokensPorMes,
		@Nullable BigDecimal assistenteUsdPorMes) {
}
