package dev.aurora.contratos.model;

import org.jspecify.annotations.Nullable;

/**
 * Segmento do contrato enviado à IA: a cláusula (se identificada), a página e o texto.
 */
public record Trecho(@Nullable String clausula, int pagina, String texto) {
}
