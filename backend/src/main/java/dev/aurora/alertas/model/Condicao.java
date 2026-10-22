package dev.aurora.alertas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Condição que deve ter um alerta aberto agora. {@code chave} identifica a condição: só
 * existe um alerta aberto por chave (RN-07, "não duplicar").
 */
public record Condicao(String chave, TipoAlerta tipo, Severidade severidade, @Nullable UUID unidadeId, String titulo,
		String descricao, String link) {
}
