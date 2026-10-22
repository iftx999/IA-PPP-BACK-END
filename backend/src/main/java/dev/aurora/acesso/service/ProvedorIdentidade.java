package dev.aurora.acesso.service;

/**
 * Contas no provedor de identidade (OIDC) do tenant, identificado pelo emissor. A Aurora
 * não guarda senhas: convite, redefinição de senha e bloqueio de login acontecem lá (ADR
 * 0003, ADR 0011).
 */
public interface ProvedorIdentidade {

	/**
	 * Cria a conta da pessoa, ou reaproveita a que já existe com o mesmo e-mail no
	 * emissor.
	 * @return identificador da conta (o {@code sub} dos tokens)
	 */
	String criarConta(String emissor, String email, String nome);

	/**
	 * Envia o convite: link de uso único e com prazo para confirmar o e-mail e definir a
	 * senha.
	 */
	void enviarConvite(String emissor, String sub);

	/** Envia link de uso único para a pessoa definir uma nova senha. */
	void enviarRedefinicaoSenha(String emissor, String sub);

	/**
	 * Habilita ou bloqueia o login. Ao bloquear, encerra as sessões abertas (RN-16).
	 */
	void definirAtiva(String emissor, String sub, boolean ativa);

}
