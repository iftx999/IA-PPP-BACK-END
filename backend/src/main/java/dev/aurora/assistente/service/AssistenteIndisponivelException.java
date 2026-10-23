package dev.aurora.assistente.service;

import dev.aurora.comum.ServicoExternoIndisponivelException;

/** Serviço de IA desligado, recusando a chave ou fora do ar (503 com a orientação). */
public class AssistenteIndisponivelException extends ServicoExternoIndisponivelException {

	public AssistenteIndisponivelException(String mensagem) {
		super(mensagem);
	}

	public AssistenteIndisponivelException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}

}
