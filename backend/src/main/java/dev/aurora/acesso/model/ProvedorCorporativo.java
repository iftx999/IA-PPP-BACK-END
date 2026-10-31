package dev.aurora.acesso.model;

import org.jspecify.annotations.Nullable;

/**
 * IdP corporativo do tenant (S10, ADR 0025): o diretório da empresa (ex.: Entra ID) por
 * onde as pessoas convidadas podem entrar. Fica no realm do tenant; o segredo do client
 * nunca sai do provedor de identidade.
 *
 * @param protocolo OIDC ou SAML
 * @param nome texto do botão na tela de login ("Entrar com ...")
 * @param endereco documento de descoberta OIDC ou metadados SAML do IdP
 * @param clienteId client registrado no IdP (só OIDC)
 * @param habilitado com {@code false}, o botão some e o login pelo IdP é recusado
 */
public record ProvedorCorporativo(Protocolo protocolo, String nome, String endereco, @Nullable String clienteId,
		boolean habilitado) {

	/** Apelido do IdP no realm: aparece no endereço de retorno e no claim {@code idp}. */
	public static final String APELIDO = "corporativo";

	public enum Protocolo {

		OIDC, SAML

	}

}
