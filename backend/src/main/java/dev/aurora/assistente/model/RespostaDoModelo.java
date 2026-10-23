package dev.aurora.assistente.model;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * @param recusada o modelo recusou responder (classificador de segurança ou filtro de
 * conteúdo)
 * @param custoUsd custo pela tabela do provedor; nulo quando não há preço para o modelo
 */
public record RespostaDoModelo(String texto, String modelo, Uso uso, boolean recusada, @Nullable BigDecimal custoUsd) {
}
