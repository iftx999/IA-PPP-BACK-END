package dev.aurora.integracoes.model;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

public record DadosFonte(String nome, TipoAdaptador tipo, @Nullable String sistemaOrigem, @Nullable String frequencia,
		Duration atrasoMaximo, boolean ativo) {
}
