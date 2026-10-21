package dev.aurora.comum;

/** Os dados são válidos no formato, mas violam uma regra de negócio. Responde 422. */
public class RegraVioladaException extends RuntimeException {

	public RegraVioladaException(String mensagem) {
		super(mensagem);
	}

}
