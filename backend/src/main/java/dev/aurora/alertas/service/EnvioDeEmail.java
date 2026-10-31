package dev.aurora.alertas.service;

/** Porta de saída de e-mail (SMTP em produção; desligada sem servidor configurado). */
public interface EnvioDeEmail {

	boolean ligado();

	/**
	 * @throws RuntimeException se o servidor recusar ou estiver fora
	 */
	void enviar(String para, String assunto, String texto, String html);

}
