package dev.aurora.auditoria.model;

import java.time.Instant;

/**
 * Um expurgo da trilha (ADR 0029): apagou {@code quantidade} eventos até
 * {@code ateSequencia}, todos anteriores a {@code limite}; {@code ultimoHash} é o hash do
 * último apagado, para onde aponta o primeiro evento que ficou.
 */
public record Expurgo(Instant executadoEm, Instant limite, long ateSequencia, long quantidade, byte[] ultimoHash) {
}
