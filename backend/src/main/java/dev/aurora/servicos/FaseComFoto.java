package dev.aurora.servicos;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Fase da OS em que o agente fotografa no app (ADR 0053). O encerramento não entra aqui:
 * as fotos dele são parâmetros do serviço.
 */
public enum FaseComFoto {

	INICIAR, PARALISAR, RETOMAR, DEVOLVER;

	/** Padrão de serviço novo e das versões anteriores à V40: foto em todas as fases. */
	public static final List<FaseComFoto> TODAS = List.of(values());

	/** Sem repetição e na ordem da OS (base da comparação de versões); nulo: todas. */
	public static List<FaseComFoto> normalizadas(@Nullable Collection<FaseComFoto> fases) {
		if (fases == null) {
			return TODAS;
		}
		return fases.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(fases));
	}

}
