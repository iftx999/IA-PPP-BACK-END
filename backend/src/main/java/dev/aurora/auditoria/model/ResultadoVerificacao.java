package dev.aurora.auditoria.model;

import org.jspecify.annotations.Nullable;

/**
 * Resultado da verificação da trilha: {@code primeiraQuebra} é a sequência do primeiro
 * evento com elo ou hash inválido (nula se a cadeia está íntegra).
 */
public record ResultadoVerificacao(long eventos, long verificados, long legados, @Nullable Long primeiraQuebra) {

	public boolean integra() {
		return primeiraQuebra == null;
	}

}
