package dev.aurora.acesso;

import java.util.UUID;

/**
 * Dados iniciais de um cliente criado pela administração da plataforma (S1, ADR 0027):
 * unidade raiz, perfis do sistema e o primeiro administrador, convidado por e-mail pelo
 * mesmo fluxo da tela de Usuários. Idempotente: rodar de novo completa o que faltou.
 */
public interface ProvisionamentoDeCliente {

	/**
	 * Roda fora de qualquer transação (cada etapa abre a sua, no contexto do cliente). O
	 * emissor do cliente já precisa estar cadastrado.
	 * @return se o primeiro administrador foi convidado agora (falso se já existia)
	 */
	boolean provisionar(UUID tenantId, String nomeCliente, String nomeAdministrador, String emailAdministrador);

	/** Reenvia o convite do primeiro administrador enquanto ele não entrou. */
	void reenviarConviteDoAdministrador(UUID tenantId, String emailAdministrador);

	/** Usuários ativos do cliente (com convite pendente), para o limite contratado. */
	long contarUsuariosAtivos(UUID tenantId);

}
