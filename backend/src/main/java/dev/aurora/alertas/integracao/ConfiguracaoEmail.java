package dev.aurora.alertas.integracao;

import java.util.Properties;

import dev.aurora.alertas.service.EnvioDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Monta o envio a partir de {@code aurora.email} (não usa o {@code spring.mail.*}, para
 * que um host vazio desligue o envio em vez de falhar a cada alerta).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesEmail.class)
class ConfiguracaoEmail {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoEmail.class);

	@Bean
	EnvioDeEmail envioDeEmail(PropriedadesEmail propriedades) {
		if (!propriedades.configurado()) {
			LOG.info("SMTP não configurado (aurora.email.host): alertas não serão enviados por e-mail");
			return new EnvioEmailDesligado();
		}
		var remetente = new JavaMailSenderImpl();
		remetente.setHost(propriedades.host());
		remetente.setPort(propriedades.porta());
		if (propriedades.usuario() != null && !propriedades.usuario().isBlank()) {
			remetente.setUsername(propriedades.usuario());
			remetente.setPassword(propriedades.senha());
		}
		remetente.setDefaultEncoding("UTF-8");
		var extras = new Properties();
		extras.put("mail.smtp.auth", String.valueOf(remetente.getUsername() != null));
		extras.put("mail.smtp.starttls.enable", String.valueOf(propriedades.tls()));
		extras.put("mail.smtp.starttls.required", String.valueOf(propriedades.tls()));
		extras.put("mail.smtp.connectiontimeout", "10000");
		extras.put("mail.smtp.timeout", "10000");
		extras.put("mail.smtp.writetimeout", "10000");
		remetente.setJavaMailProperties(extras);
		return new EnvioEmailSmtp(remetente, propriedades.remetente());
	}

}
