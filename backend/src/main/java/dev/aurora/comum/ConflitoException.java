package dev.aurora.comum;

/** A operação conflita com o estado atual (ex.: vigência sobreposta). Responde 409. */
public class ConflitoException extends RuntimeException {

	public ConflitoException(String mensagem) {
		super(mensagem);
	}

}
