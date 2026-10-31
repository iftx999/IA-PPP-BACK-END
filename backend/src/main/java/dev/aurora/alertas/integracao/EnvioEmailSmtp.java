package dev.aurora.alertas.integracao;

import dev.aurora.alertas.service.EnvioDeEmail;
import jakarta.mail.MessagingException;

import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** E-mail por SMTP, com versão em texto e em HTML. */
class EnvioEmailSmtp implements EnvioDeEmail {

	private final JavaMailSender remetente;

	private final String de;

	EnvioEmailSmtp(JavaMailSender remetente, String de) {
		this.remetente = remetente;
		this.de = de;
	}

	@Override
	public boolean ligado() {
		return true;
	}

	@Override
	public void enviar(String para, String assunto, String texto, String html) {
		var mensagem = remetente.createMimeMessage();
		try {
			var montador = new MimeMessageHelper(mensagem, true, "UTF-8");
			montador.setFrom(de);
			montador.setTo(para);
			montador.setSubject(assunto);
			montador.setText(texto, html);
		}
		catch (MessagingException erro) {
			throw new MailPreparationException(erro);
		}
		remetente.send(mensagem);
	}

}
