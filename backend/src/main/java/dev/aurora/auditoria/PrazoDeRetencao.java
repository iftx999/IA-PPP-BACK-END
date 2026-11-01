package dev.aurora.auditoria;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Prazo de retenção da trilha do tenant e o último expurgo, se houve (ADR 0029).
 *
 * @param ultimoExpurgoLimite o último expurgo apagou eventos anteriores a este instante
 * @param totalExpurgado eventos já removidos pelo prazo, somando todos os expurgos
 */
@Schema(name = "RetencaoAuditoria")
public record PrazoDeRetencao(int meses, int minimo, int maximo, @Nullable Instant ultimoExpurgoEm,
		@Nullable Instant ultimoExpurgoLimite, long totalExpurgado) {
}
