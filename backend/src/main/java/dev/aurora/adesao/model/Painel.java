package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Painel de adesão de uma unidade em uma competência (épico B). */
public record Painel(UnidadeResumo unidade, YearMonth competencia, List<YearMonth> competenciasDisponiveis,
		Adesao adesao, Composicao composicao, BigDecimal variacaoPp, Situacao situacao, MetaVigente meta,
		Projecao projecao, List<PontoSerie> serie, List<LinhaUnidade> filhas) {
}
