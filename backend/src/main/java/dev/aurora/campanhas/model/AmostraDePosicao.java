package dev.aurora.campanhas.model;

import java.time.Instant;

/** Posição do celular durante a jornada (RN-27). */
public record AmostraDePosicao(Instant momento, Posicao posicao) {
}
