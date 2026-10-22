package dev.aurora.acesso.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Perfil concedido ao usuário em uma unidade (vale também nas descendentes). */
public record ConcessaoDoUsuario(UUID id, UUID perfilId, String perfil, UUID unidadeId, String unidade,
		@Nullable OffsetDateTime validoAte) {
}
