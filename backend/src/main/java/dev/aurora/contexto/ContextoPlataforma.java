package dev.aurora.contexto;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Quem administra a plataforma (S1, ADR 0027): cria e configura clientes, mas nunca age
 * dentro de um. No banco vira {@code app.plataforma = on}, que só alcança as tabelas de
 * cadastro do cliente; com um {@link ContextoAcesso} ativo, vale o do cliente.
 *
 * @param operador {@code sub} do token do realm da plataforma (vai para a trilha, sem
 * nome nem e-mail)
 */
public record ContextoPlataforma(String operador) {

	private static final ThreadLocal<ContextoPlataforma> ATUAL = new ThreadLocal<>();

	public ContextoPlataforma {
		Objects.requireNonNull(operador, "operador");
	}

	public static Optional<ContextoPlataforma> atual() {
		return Optional.ofNullable(ATUAL.get());
	}

	public static ContextoPlataforma exigir() {
		return atual().orElseThrow(() -> new IllegalStateException("Sem contexto de plataforma"));
	}

	public static <T> T executar(ContextoPlataforma contexto, Callable<T> acao) throws Exception {
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
