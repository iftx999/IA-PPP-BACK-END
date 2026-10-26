package dev.aurora.acesso.service;

/**
 * O token foi emitido sem o segundo fator e a política o exige (H9): a pessoa precisa
 * entrar de novo, com o código (ou cadastrá-lo).
 */
public class SegundoFatorNecessarioException extends RuntimeException {

	/** Código no corpo da resposta 403, para o front distinguir de falta de permissão. */
	public static final String CODIGO = "SEGUNDO_FATOR_NECESSARIO";

	public SegundoFatorNecessarioException() {
		super("A verificação em duas etapas é obrigatória para o seu acesso. Entre de novo informando o código.");
	}

}
