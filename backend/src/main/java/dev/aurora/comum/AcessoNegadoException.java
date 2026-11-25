package dev.aurora.comum;

/**
 * Acesso recusado por uma regra do recurso (ex.: celular revogado). Responde 403 com o
 * {@code codigo} para o cliente tratar o caso.
 */
public class AcessoNegadoException extends RuntimeException {

	private final String codigo;

	public AcessoNegadoException(String mensagem, String codigo) {
		super(mensagem);
		this.codigo = codigo;
	}

	public String codigo() {
		return codigo;
	}

}
