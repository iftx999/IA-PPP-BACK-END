package dev.aurora.integracoes.model;

/**
 * Uma linha da carga como chegou: matrícula (código no sistema comercial), código da
 * unidade e situação. Nunca nome nem endereço (RN-09).
 */
public record RegistroCarga(int linha, String matricula, String unidade, String situacao) {
}
