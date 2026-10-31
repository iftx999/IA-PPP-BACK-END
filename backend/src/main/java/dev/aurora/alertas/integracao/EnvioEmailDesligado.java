package dev.aurora.alertas.integracao;

import dev.aurora.alertas.service.EnvioDeEmail;

/** Sem SMTP configurado: nada é enviado e a fila de notificação não avança. */
class EnvioEmailDesligado implements EnvioDeEmail {

	@Override
	public boolean ligado() {
		return false;
	}

	@Override
	public void enviar(String para, String assunto, String texto, String html) {
		throw new IllegalStateException("Envio de e-mail desligado");
	}

}
