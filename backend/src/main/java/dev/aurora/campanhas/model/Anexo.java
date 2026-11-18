package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Foto de uma OS enviada pelo app em partes (10.9.3).
 *
 * @param parametro chave do parâmetro de foto; nulo na foto da devolução
 * @param situacao RECEBENDO, DISPONIVEL ou RECUSADO
 */
public record Anexo(UUID id, UUID osId, @Nullable String parametro, String tipoMidia, int tamanho, int recebido,
		String sha256, String situacao, @Nullable String recusa, UUID usuarioId, Instant criadoEm,
		@Nullable Instant concluidoEm) {

	public boolean completo() {
		return recebido == tamanho;
	}

}
