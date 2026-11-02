package dev.aurora.campanhas.model;

/** Quantas OS terminaram com cada motivo de recusa ou inviabilidade (I4). */
public record ContagemMotivo(StatusOrdem status, MotivoResultado motivo, long quantidade) {
}
