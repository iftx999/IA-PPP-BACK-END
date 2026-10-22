package dev.aurora.integracoes.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Autenticação do Agente Aurora, antes de existir contexto de tenant: lê a credencial
 * pelo prefixo ({@code credencial_conector} não tem RLS) e controla o limite de
 * requisições.
 */
@Repository
public class CredencialRepository {

	private final JdbcClient jdbc;

	CredencialRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record CredencialAtiva(UUID tenantId, UUID fonteId, byte[] hash) {
	}

	public Optional<CredencialAtiva> buscarAtiva(String prefixo) {
		return jdbc.sql("SELECT tenant_id, fonte_dados_id, hash FROM credencial_conector WHERE prefixo = ? AND ativo")
			.param(prefixo)
			.query((r, n) -> new CredencialAtiva(r.getObject("tenant_id", UUID.class),
					r.getObject("fonte_dados_id", UUID.class), r.getBytes("hash")))
			.optional();
	}

	/** Conta a requisição no minuto atual e devolve o total do minuto. */
	public int contarRequisicao(String chave) {
		return jdbc.sql("""
				INSERT INTO limite_requisicao (chave, minuto, contagem)
				VALUES (?, floor(extract(epoch FROM now()) / 60)::bigint, 1)
				ON CONFLICT (chave, minuto) DO UPDATE SET contagem = limite_requisicao.contagem + 1
				RETURNING contagem""").param(chave).query(Integer.class).single();
	}

}
