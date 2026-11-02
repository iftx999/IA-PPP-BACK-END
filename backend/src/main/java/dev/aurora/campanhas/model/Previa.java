package dev.aurora.campanhas.model;

import java.time.YearMonth;

/**
 * Pré-visualização da campanha (I2): quantos imóveis entram, por situação e prioridade, e
 * quantos ficam de fora por já terem OS aberta.
 */
public record Previa(YearMonth competencia, long imoveis, long factiveis, long cortados, long comOsAberta, long alta,
		long media, long baixa, long limite, ImpactoNaMeta impacto) {
}
