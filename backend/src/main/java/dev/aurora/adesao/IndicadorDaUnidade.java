package dev.aurora.adesao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Adesão de uma unidade na competência com tudo o que a explica (RN-01 a RN-06): ligados
 * e com rede, variação sobre o mês anterior, meta aplicável (própria ou herdada, com a
 * origem contratual), semáforo e composição dos sem ligação. Valores nulos = sem dado.
 *
 * @param faltaParaMetaPp pontos percentuais até a meta (zero se atingida)
 * @param metaHerdadaDe nome da unidade de onde a meta veio, quando herdada
 */
public record IndicadorDaUnidade(UUID unidadeId, YearMonth competencia, long ligados, long comRede,
		@Nullable BigDecimal adesao, @Nullable BigDecimal adesaoAnterior, @Nullable BigDecimal variacaoPp,
		@Nullable BigDecimal valorMeta, @Nullable BigDecimal valorAlerta, @Nullable BigDecimal faltaParaMetaPp,
		@Nullable LocalDate vigenciaFimMeta, @Nullable String origemMeta, @Nullable String metaHerdadaDe,
		Situacao situacao, long factiveis, long cortados) {
}
