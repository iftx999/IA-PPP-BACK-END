package dev.aurora.integracoes.model;

/**
 * Registro gravado, mas que merece conferência na origem (ex.: imóvel que já teve conexão
 * física e veio como factível). Só a matrícula, nunca nome ou endereço (RN-09).
 */
public record Aviso(String matricula, String mensagem) {
}
