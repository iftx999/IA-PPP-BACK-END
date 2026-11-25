package dev.aurora.campanhas.integracao;

import org.jspecify.annotations.Nullable;

/**
 * Porta do envio de SMS (aviso de chegada, ADR 0052). O provedor é contratado pela
 * empresa; até lá vale {@link SmsSoRegistro}, que não envia nada.
 */
public interface EnvioDeSms {

	/**
	 * Resultado do envio; {@code enviado} falso com {@code erro} nulo = só registrado.
	 */
	record Resultado(boolean enviado, String provedor, @Nullable String erro) {

		public static Resultado registrado(String provedor) {
			return new Resultado(false, provedor, null);
		}

	}

	/**
	 * @param telefone em E.164 (+55…); nunca registrar em log
	 */
	Resultado enviar(String telefone, String texto);

}
