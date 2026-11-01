package dev.aurora.acesso.model;

import java.util.List;
import java.util.UUID;

/** Dados do usuário autenticado e do tenant em que está atuando. */
public record UsuarioAutenticado(UUID usuarioId, String nome, String email, UUID tenantId, String tenant,
		String tenantCodigo, boolean tenantTemLogo, List<String> permissoes) {
}
