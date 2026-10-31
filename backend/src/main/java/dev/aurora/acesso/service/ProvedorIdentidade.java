package dev.aurora.acesso.service;

import java.time.Instant;
import java.util.Optional;

import dev.aurora.acesso.model.ProvedorCorporativo;
import org.jspecify.annotations.Nullable;

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

	/**
	 * Quando a pessoa cadastrou o segundo fator (TOTP) mais recente; vazio se não tem
	 * (H9).
	 */
	Optional<Instant> segundoFatorCadastradoEm(String emissor, String sub);

	/**
	 * Pede o cadastro do segundo fator no próximo login, se a pessoa ainda não tem um.
	 */
	void exigirCadastroSegundoFator(String emissor, String sub);

	/**
	 * Remove o segundo fator (ex.: celular perdido). Se a política exigir, a pessoa
	 * cadastra outro no próximo login.
	 */
	void removerSegundoFator(String emissor, String sub);

	/** IdP corporativo configurado no emissor (S10); vazio se não há. */
	Optional<ProvedorCorporativo> provedorCorporativo(String emissor);

	/**
	 * Cria ou atualiza o IdP corporativo a partir do endereço de descoberta (OIDC) ou dos
	 * metadados (SAML). Só contas já existentes no emissor podem entrar por ele.
	 * @param segredo segredo do client no IdP (OIDC); {@code null} mantém o atual
	 * @throws dev.aurora.comum.RegraVioladaException se o endereço não traz uma
	 * configuração válida do protocolo
	 */
	void salvarProvedorCorporativo(String emissor, ProvedorCorporativo provedor, @Nullable String segredo);

	/** Remove o IdP corporativo e os vínculos das contas com ele. */
	void removerProvedorCorporativo(String emissor);

}
