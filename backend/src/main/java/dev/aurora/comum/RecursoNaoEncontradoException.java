package dev.aurora.comum;

/**
 * Recurso inexistente, de outro tenant ou fora do escopo. Os três casos respondem igual
 * (404), para não revelar a existência de dados alheios.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

	public RecursoNaoEncontradoException() {
		super("Recurso não encontrado");
	}

}
