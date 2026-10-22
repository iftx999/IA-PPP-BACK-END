package dev.aurora.acesso.model;

/** Ciclo de vida do usuário (H8). */
public enum SituacaoUsuario {

	/** Convidado e ainda sem primeiro acesso. */
	CONVITE_PENDENTE,

	ATIVO,

	/** Desativado: não acessa, mas histórico e auditoria são preservados (S7). */
	INATIVO

}
