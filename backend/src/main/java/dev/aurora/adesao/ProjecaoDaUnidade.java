package dev.aurora.adesao;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Projeção "no ritmo" (RN-05) de uma unidade na competência mais recente, com a meta
 * aplicável (própria ou herdada) que tem fim de vigência. Base do alerta de ritmo.
 *
 * @param ritmoAtual novas ligações por mês; nulo quando não há meses comparáveis
 * @param ritmoNecessario ligações por mês para chegar à meta no prazo; nulo no mês do
 * prazo
 * @param mesAtingimento quando a meta sai no ritmo atual; nulo se o ritmo não é positivo
 */
public record ProjecaoDaUnidade(UUID unidadeId, String unidadeNome, String tipo, YearMonth competencia,
		BigDecimal adesao, BigDecimal meta, @Nullable BigDecimal alerta, boolean metaPropria,
		@Nullable BigDecimal ritmoAtual, @Nullable BigDecimal ritmoNecessario, long faltam, int mesesRestantes,
		BigDecimal projecaoNoPrazo, YearMonth prazo, @Nullable YearMonth mesAtingimento, boolean atinge) {
}
