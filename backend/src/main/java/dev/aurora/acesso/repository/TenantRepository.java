package dev.aurora.acesso.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

	private final JdbcClient jdbc;

	TenantRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Tabela sem RLS: é consultada antes de existir contexto de tenant. */
	public Optional<UUID> buscarPorEmissor(String emissor) {
		return jdbc.sql("SELECT tenant_id FROM tenant_emissor WHERE emissor = ?")
			.param(emissor)
			.query(UUID.class)
			.optional();
	}

	/** Exige contexto de tenant: o RLS só deixa ver o próprio tenant. */
	public boolean tenantAtualAtivo() {
		return jdbc.sql("SELECT ativo FROM tenant").query(Boolean.class).optional().orElse(false);
	}

	/** Política do tenant atual: segundo fator exigido de todos os usuários (H9). */
	public boolean segundoFatorObrigatorio() {
		return jdbc.sql("SELECT segundo_fator_obrigatorio FROM tenant").query(Boolean.class).optional().orElse(false);
	}

	public void definirSegundoFatorObrigatorio(boolean obrigatorio) {
		jdbc.sql("UPDATE tenant SET segundo_fator_obrigatorio = ?").param(obrigatorio).update();
	}

	/**
	 * Política do tenant atual: a verificação em duas etapas feita pelo IdP corporativo
	 * vale no lugar do TOTP da Aurora (S10, ADR 0025).
	 */
	public boolean ssoConfiaSegundoFator() {
		return jdbc.sql("SELECT sso_confia_segundo_fator FROM tenant").query(Boolean.class).optional().orElse(false);
	}

	public void definirSsoConfiaSegundoFator(boolean confia) {
		jdbc.sql("UPDATE tenant SET sso_confia_segundo_fator = ?").param(confia).update();
	}

	/**
	 * Emissor OIDC (realm) do tenant: é onde a administração de usuários cria e altera
	 * contas.
	 */
	public Optional<String> buscarEmissor(UUID tenantId) {
		return jdbc.sql("SELECT emissor FROM tenant_emissor WHERE tenant_id = ? ORDER BY emissor LIMIT 1")
			.param(tenantId)
			.query(String.class)
			.optional();
	}

	/**
	 * Tenants com emissor cadastrado (tabela sem RLS: lida antes de existir contexto).
	 */
	public List<UUID> listarComEmissor() {
		return jdbc.sql("SELECT DISTINCT tenant_id FROM tenant_emissor ORDER BY tenant_id").query(UUID.class).list();
	}

	/** Caminhos das unidades raiz do tenant atual (escopo do tenant inteiro). */
	public List<String> listarRaizes() {
		return jdbc.sql("SELECT caminho::text FROM unidade WHERE nlevel(caminho) = 1 AND ativo")
			.query(String.class)
			.list();
	}

}
