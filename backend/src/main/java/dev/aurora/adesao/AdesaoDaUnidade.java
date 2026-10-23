package dev.aurora.adesao;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Adesão de uma unidade na competência e o semáforo pela meta aplicável (RN-01, RN-03,
 * RN-06); percentual nulo quando não há medição.
 */
public record AdesaoDaUnidade(UUID unidadeId, @Nullable BigDecimal percentual, Situacao situacao) {
}
