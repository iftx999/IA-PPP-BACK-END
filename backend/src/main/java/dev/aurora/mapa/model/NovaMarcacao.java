package dev.aurora.mapa.model;

import org.jspecify.annotations.Nullable;

public record NovaMarcacao(double longitude, double latitude, String titulo, @Nullable String observacao) {
}
