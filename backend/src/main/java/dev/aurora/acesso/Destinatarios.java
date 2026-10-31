package dev.aurora.acesso;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Quem recebe avisos do sistema (ex.: alertas por e-mail, ADR 0024). */
public interface Destinatarios {

	/**
	 * Usuários ativos com a permissão numa concessão vigente que cobre a unidade (ela ou
	 * uma acima). Sem unidade: quem tem a permissão em qualquer unidade.
	 */
	List<Destinatario> comPermissao(String permissao, @Nullable UUID unidade);

	record Destinatario(UUID usuarioId, String nome, String email) {
	}

}
