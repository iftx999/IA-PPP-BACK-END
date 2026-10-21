package dev.aurora.auditoria.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import dev.aurora.auditoria.model.Elo;
import dev.aurora.auditoria.model.EventoGravado;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Trilha do tenant atual (RLS). A tabela é somente inserção: a aplicação não tem UPDATE
 * nem DELETE nela.
 */
@Repository
public class TrilhaRepository {

	private final JdbcClient jdbc;

	TrilhaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Forma canônica do IP como o PostgreSQL grava (ex.: "::1"), para assinar exatamente
	 * o que fica salvo.
	 */
	public @Nullable String normalizarIp(@Nullable String ip) {
		if (ip == null) {
			return null;
		}
		return jdbc.sql("SELECT host(?::inet)").param(ip).query(String.class).single();
	}

	/**
	 * Trava o último elo do tenant até o fim da transação (serializa a gravação de
	 * eventos).
	 */
	public Elo travarUltimoElo(UUID tenantId) {
		jdbc.sql("""
				INSERT INTO cadeia_auditoria (tenant_id, sequencia, ultimo_hash) VALUES (?, 0, ?)
				ON CONFLICT (tenant_id) DO NOTHING""").param(tenantId).param(Elo.HASH_INICIAL).update();
		return jdbc.sql("SELECT sequencia, ultimo_hash FROM cadeia_auditoria WHERE tenant_id = ? FOR UPDATE")
			.param(tenantId)
			.query((linha, n) -> new Elo(linha.getLong("sequencia"), linha.getBytes("ultimo_hash")))
			.single();
	}

	public void inserir(UUID id, UUID tenantId, long sequencia, Instant ocorridoEm, @Nullable UUID usuarioId,
			String tipo, @Nullable String recurso, String detalhesJson, @Nullable String ipOrigem, byte[] hashAnterior,
			byte[] hash, int formato) {
		jdbc.sql(
				"""
						INSERT INTO evento_auditoria (id, tenant_id, sequencia, ocorrido_em, usuario_id, tipo, recurso, detalhes,
						                              ip_origem, hash_anterior, hash, formato_hash)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::inet, ?, ?, ?)""")
			.params(id, tenantId, sequencia, Timestamp.from(ocorridoEm), usuarioId, tipo, recurso, detalhesJson,
					ipOrigem, hashAnterior, hash, formato)
			.update();
		jdbc.sql("UPDATE cadeia_auditoria SET sequencia = ?, ultimo_hash = ? WHERE tenant_id = ?")
			.params(sequencia, hash, tenantId)
			.update();
	}

	/** Percorre a trilha em ordem, sem carregar tudo na memória. */
	public void percorrer(Consumer<EventoGravado> consumidor) {
		try (var eventos = jdbc.sql("""
				SELECT id, tenant_id, sequencia, ocorrido_em, usuario_id, tipo, recurso, detalhes::text AS detalhes,
				       host(ip_origem) AS ip_origem, hash_anterior, hash, formato_hash
				  FROM evento_auditoria ORDER BY sequencia""")
			.query((linha, n) -> new EventoGravado(linha.getObject("id", UUID.class),
					linha.getObject("tenant_id", UUID.class), linha.getLong("sequencia"),
					linha.getTimestamp("ocorrido_em").toInstant(), linha.getObject("usuario_id", UUID.class),
					linha.getString("tipo"), linha.getString("recurso"), linha.getString("detalhes"),
					linha.getString("ip_origem"), linha.getBytes("hash_anterior"), linha.getBytes("hash"),
					(Integer) linha.getObject("formato_hash")))
			.stream()) {
			eventos.forEach(consumidor);
		}
	}

}
