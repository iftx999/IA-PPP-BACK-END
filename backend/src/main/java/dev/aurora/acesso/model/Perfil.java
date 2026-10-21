package dev.aurora.acesso.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Conjunto de permissões. Perfis do sistema não são editáveis; {@code usuarios} = quantos
 * o usam hoje.
 */
public record Perfil(UUID id, String nome, @Nullable String descricao, boolean sistema, List<String> permissoes,
		long usuarios) {
}
