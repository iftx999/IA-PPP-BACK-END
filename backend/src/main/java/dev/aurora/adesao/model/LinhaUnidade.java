package dev.aurora.adesao.model;

import dev.aurora.adesao.Situacao;

import java.math.BigDecimal;

/** Linha da tabela "por unidade" do painel (B4). */
public record LinhaUnidade(UnidadeResumo unidade, Adesao adesao, BigDecimal variacaoPp, Situacao situacao,
		MetaVigente meta) {
}
