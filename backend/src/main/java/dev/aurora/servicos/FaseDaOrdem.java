package dev.aurora.servicos;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Fase da OS em que o app pode exigir foto e observação (ADR 0053). O encerramento não
 * entra aqui: o que ele pede são os parâmetros do serviço.
 */
public enum FaseDaOrdem {

	INICIAR, PARALISAR, RETOMAR, DEVOLVER;

	/** Padrão de serviço novo e das versões anteriores à V40: todas as fases. */
	public static final List<FaseDaOrdem> TODAS = List.of(values());

	/** Sem repetição e na ordem da OS (base da comparação de versões); nulo: todas. */
	public static List<FaseDaOrdem> normalizadas(@Nullable Collection<FaseDaOrdem> fases) {
		if (fases == null) {
			return TODAS;
		}
		return fases.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(fases));
	}

}
