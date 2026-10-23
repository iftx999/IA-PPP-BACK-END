package dev.aurora.comum;

/** Cota de uso do cliente esgotada (ex.: limite mensal do assistente): 429. */
public class LimiteDeUsoException extends RuntimeException {

	public LimiteDeUsoException(String mensagem) {
		super(mensagem);
	}

}
