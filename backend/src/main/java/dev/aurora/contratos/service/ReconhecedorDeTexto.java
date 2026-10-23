package dev.aurora.contratos.service;

/**
 * OCR de páginas digitalizadas (ADR 0021). Roda dentro da infraestrutura da empresa: o
 * conteúdo do contrato não sai para serviço externo.
 */
public interface ReconhecedorDeTexto {

	/** Falso quando o OCR não está instalado neste ambiente. */
	boolean disponivel();

	/**
	 * Texto da imagem (PNG) de uma página.
	 * @throws ReconhecimentoFalhouException tempo esgotado ou erro do OCR
	 */
	String reconhecer(byte[] png) throws ReconhecimentoFalhouException;

	class ReconhecimentoFalhouException extends Exception {

		public ReconhecimentoFalhouException(String mensagem, Throwable causa) {
			super(mensagem, causa);
		}

		public ReconhecimentoFalhouException(String mensagem) {
			super(mensagem);
		}

	}

}
