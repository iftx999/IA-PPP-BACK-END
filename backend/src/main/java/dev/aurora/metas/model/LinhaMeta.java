package dev.aurora.metas.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import org.jspecify.annotations.Nullable;

/**
 * Unidade na árvore de metas (C1): meta própria vigente ou, sem ela, a herdada; e a
 * adesão da última competência medida para a situação.
 */
public record LinhaMeta(UUID unidadeId, String unidadeNome, String tipo, @Nullable UUID unidadePaiId, int nivel,
		@Nullable MetaDaUnidade propria, @Nullable MetaHerdada herdada, @Nullable BigDecimal adesao,
		@Nullable YearMonth competenciaAdesao) {

	public Situacao situacao() {
		if (propria != null) {
			return Situacao.classificar(adesao, propria.valor(), propria.alerta());
		}
		if (herdada != null) {
			return Situacao.classificar(adesao, herdada.valor(), herdada.alerta());
		}
		return Situacao.SEM_META;
	}

}
