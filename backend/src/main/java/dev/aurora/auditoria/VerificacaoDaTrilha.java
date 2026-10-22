package dev.aurora.auditoria;

import org.jspecify.annotations.Nullable;

/**
 * Conferência da cadeia do tenant atual para outros módulos (ex.: verificação periódica).
 */
public interface VerificacaoDaTrilha {

	/** Sequência do primeiro evento com elo ou assinatura inválidos; nulo se íntegra. */
	@Nullable Long primeiraQuebra();

}
