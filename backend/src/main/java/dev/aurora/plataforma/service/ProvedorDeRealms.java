package dev.aurora.plataforma.service;

/**
 * Realm de cada cliente no provedor de identidade (ADR 0003, ADR 0027), criado a partir
 * do modelo de produção (tema, fluxos de login e SSO, conta de serviço da API).
 */
public interface ProvedorDeRealms {

	/** Emissor (iss dos tokens) do realm do cliente com esse código. */
	String emissor(String codigo);

	/** Se já existe um realm com esse nome (de qualquer dono). */
	boolean existe(String codigo);

	/**
	 * Cria o realm. Só a conta da plataforma passa a administrá-lo (além da conta de
	 * serviço da API dentro dele).
	 * @return falso se o realm já existia
	 */
	boolean criar(String codigo, String nomeCliente);

}
