package dev.aurora.contratos.service;

import java.util.List;

import dev.aurora.contratos.model.Pagina;

/** Lê o texto de cada página de um PDF. */
public interface LeitorPdf {

	/** Quantas páginas o PDF tem; falha se o arquivo não é um PDF válido. */
	int contarPaginas(byte[] pdf) throws PdfInvalidoException;

	List<Pagina> lerPaginas(byte[] pdf) throws PdfInvalidoException;

	class PdfInvalidoException extends Exception {

		public PdfInvalidoException(String mensagem, Throwable causa) {
			super(mensagem, causa);
		}

	}

}
