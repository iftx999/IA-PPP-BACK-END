package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A OS como o aviso de chegada precisa: situação, equipe, serviço e o contato (cifrado).
 */
public record OrdemParaAviso(UUID id, String status, String alvo, @Nullable UUID equipeId, String servicoNome,
		@Nullable String telefoneCifrado, @Nullable String finalTelefone) {
}
