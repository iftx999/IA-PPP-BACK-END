package dev.aurora.contexto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Quem está agindo e onde: tenant, usuário, permissões efetivas e escopo (caminhos ltree
 * das unidades). Resolvido no servidor a cada requisição; nunca vem do corpo da
 * requisição nem do texto enviado à IA.
 */
public record ContextoAcesso(UUID tenantId, UUID usuarioId, Set<String> permissoes, List<String> escopos) {

	private static final ThreadLocal<ContextoAcesso> ATUAL = new ThreadLocal<>();

	public ContextoAcesso {
		Objects.requireNonNull(tenantId, "tenantId");
		permissoes = Set.copyOf(permissoes);
		escopos = List.copyOf(escopos);
	}

	/**
	 * Contexto só com o tenant, usado enquanto o usuário ainda está sendo identificado.
	 */
	public static ContextoAcesso somenteTenant(UUID tenantId) {
		return new ContextoAcesso(tenantId, null, Set.of(), List.of());
	}

	/**
	 * Escopo inclui a unidade raiz do tenant (caminho ltree de um nível): atua sobre o
	 * tenant inteiro.
	 */
	public boolean abrangeTenantInteiro() {
		return escopos.stream().anyMatch(caminho -> caminho.indexOf('.') < 0);
	}

	public boolean pode(String permissao) {
		return permissoes.contains(permissao);
	}

	public static Optional<ContextoAcesso> atual() {
		return Optional.ofNullable(ATUAL.get());
	}

	public static ContextoAcesso exigir() {
		return atual().orElseThrow(() -> new IllegalStateException("contexto de acesso não definido"));
	}

	/**
	 * Executa a ação dentro do contexto informado (ex.: tarefas agendadas por tenant).
	 */
	public static <T> T executar(ContextoAcesso contexto, Callable<T> acao) throws Exception {
		var anterior = ATUAL.get();
		ATUAL.set(contexto);
		try {
			return acao.call();
		}
		finally {
			if (anterior == null) {
				ATUAL.remove();
			}
			else {
				ATUAL.set(anterior);
			}
		}
	}

}
