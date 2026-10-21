package dev.aurora.adesao.model;

/** Adesão de uma unidade na competência e na anterior (para a variação). */
public record MedicaoUnidade(UnidadeResumo unidade, Adesao atual, Adesao anterior) {
}
