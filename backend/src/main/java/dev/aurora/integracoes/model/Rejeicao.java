package dev.aurora.integracoes.model;

import org.jspecify.annotations.Nullable;

/**
 * Registro recusado e o motivo (a matrícula ajuda a corrigir na origem; não é dado
 * pessoal).
 */
public record Rejeicao(int linha, @Nullable String matricula, String motivo) {
}
