package dev.aurora.acesso.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Usuário do tenant, como a administração o vê (H2, H8). */
public record Usuario(UUID id, String nome, String email, @Nullable String subExterno, boolean ativo,
		@Nullable OffsetDateTime ultimoAcesso, @Nullable OffsetDateTime convidadoEm,
		List<ConcessaoDoUsuario> concessoes) {

	public SituacaoUsuario situacao() {
		if (!ativo) {
			return SituacaoUsuario.INATIVO;
		}
		return ultimoAcesso == null ? SituacaoUsuario.CONVITE_PENDENTE : SituacaoUsuario.ATIVO;
	}

	public Usuario comConcessoes(List<ConcessaoDoUsuario> concessoes) {
		return new Usuario(id, nome, email, subExterno, ativo, ultimoAcesso, convidadoEm, concessoes);
	}

}
