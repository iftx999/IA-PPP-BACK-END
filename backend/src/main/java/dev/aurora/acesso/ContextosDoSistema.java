package dev.aurora.acesso;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;

/**
 * Contextos para tarefas internas (alertas, verificação da trilha): um por tenant ativo,
 * sem usuário, com todas as permissões no tenant inteiro. Nunca usado em requisições.
 */
public interface ContextosDoSistema {

	List<ContextoAcesso> deTodosOsTenants();

	/**
	 * Contexto do sistema para um tenant (ex.: reavaliar alertas a pedido de um usuário).
	 */
	Optional<ContextoAcesso> doTenant(UUID tenant);

}
