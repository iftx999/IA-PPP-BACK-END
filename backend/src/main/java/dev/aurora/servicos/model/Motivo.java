package dev.aurora.servicos.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Motivo de paralisação, devolução, não execução ou cancelamento (RN-22).
 *
 * @param servicoId nulo: vale para todos os serviços
 */
public record Motivo(UUID id, TipoMotivo tipo, String codigo, String nome, boolean exigeObservacao,
		@Nullable UUID servicoId, boolean ativo) {
}
