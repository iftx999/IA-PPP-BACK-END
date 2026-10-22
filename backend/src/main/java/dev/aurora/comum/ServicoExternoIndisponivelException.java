package dev.aurora.comum;

/**
 * Um serviço do qual a operação depende (ex.: provedor de identidade) falhou ou não
 * respondeu. Responde 503; a operação pode ser repetida.
 */
public class ServicoExternoIndisponivelException extends RuntimeException {

	public ServicoExternoIndisponivelException(String mensagem, Throwable causa) {
		super(mensagem, causa);
	}

}
