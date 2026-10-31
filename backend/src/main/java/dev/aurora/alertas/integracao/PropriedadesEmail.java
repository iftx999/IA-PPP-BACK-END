package dev.aurora.alertas.integracao;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP dos avisos da Aurora (ADR 0024). Sem {@code host}, o envio fica desligado e os
 * alertas aparecem só na tela.
 */
@ConfigurationProperties("aurora.email")
public record PropriedadesEmail(@Nullable String host, int porta, @Nullable String usuario, @Nullable String senha,
		String remetente, boolean tls) {

	public boolean configurado() {
		return host != null && !host.isBlank();
	}

}
