package dev.aurora.campanhas.controller;

import java.time.Instant;
import java.util.UUID;

import dev.aurora.campanhas.model.Dispositivo;
import org.jspecify.annotations.Nullable;

/**
 * Celular do app de campo (J13).
 *
 * @param apagadoEm o app confirmou que apagou os dados locais depois do bloqueio
 */
record DispositivoResponse(UUID id, UUID usuarioId, String usuarioNome, String modelo, String sistema, String versaoApp,
		Instant cadastradoEm, Instant ultimoContato, boolean revogado, @Nullable Instant revogadoEm,
		@Nullable String revogadoPor, @Nullable Instant apagadoEm) {

	static DispositivoResponse de(Dispositivo d) {
		return new DispositivoResponse(d.id(), d.usuarioId(), d.usuarioNome(), d.modelo(), d.sistema(), d.versaoApp(),
				d.cadastradoEm(), d.ultimoContato(), d.revogado(), d.revogadoEm(), d.revogadoPor(), d.apagadoEm());
	}

}
