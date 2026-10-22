package dev.aurora.metas;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import org.jspecify.annotations.Nullable;

/**
 * Situação de adesão de uma unidade na competência mais recente, com a meta vigente
 * (própria ou herdada). Base dos alertas de adesão (RN-07).
 */
public record SituacaoDaUnidade(UUID unidadeId, String unidadeNome, String tipo, @Nullable BigDecimal adesao,
		@Nullable YearMonth competencia, @Nullable BigDecimal meta, @Nullable BigDecimal alerta, boolean metaPropria,
		Situacao situacao) {
}
