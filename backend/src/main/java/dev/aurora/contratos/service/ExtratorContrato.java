package dev.aurora.contratos.service;

import java.util.List;

import dev.aurora.contratos.model.ResultadoExtracao;
import dev.aurora.contratos.model.Trecho;

/**
 * Propõe itens contratuais a partir do texto do contrato (A2). Recebe só o texto e os
 * nomes das unidades do contrato: nunca dados pessoais, nem dados de outro tenant.
 */
public interface ExtratorContrato {

	/**
	 * Falso quando a IA não está configurada neste ambiente (os itens são cadastrados à
	 * mão).
	 */
	boolean disponivel();

	ResultadoExtracao extrair(List<Trecho> trechos, List<String> unidades) throws ExtracaoFalhouException;

	class ExtracaoFalhouException extends Exception {

		public ExtracaoFalhouException(String mensagem, Throwable causa) {
			super(mensagem, causa);
		}

		public ExtracaoFalhouException(String mensagem) {
			super(mensagem);
		}

	}

}
