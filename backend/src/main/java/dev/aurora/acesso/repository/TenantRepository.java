package dev.aurora.acesso.repository;

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

}
