package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Celular do app de campo (J13).
 *
 * @param apagadoEm o app confirmou que apagou os dados locais depois da revogação
 */
public record Dispositivo(UUID id, UUID usuarioId, String usuarioNome, String modelo, String sistema, String versaoApp,
		Instant cadastradoEm, Instant ultimoContato, @Nullable Instant revogadoEm, @Nullable String revogadoPor,
		@Nullable Instant apagadoEm) {

	public boolean revogado() {
		return revogadoEm != null;
	}

}
