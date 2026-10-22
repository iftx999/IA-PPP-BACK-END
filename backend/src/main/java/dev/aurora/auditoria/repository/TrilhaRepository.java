package dev.aurora.auditoria.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import dev.aurora.auditoria.model.Autor;
import dev.aurora.auditoria.model.Elo;
import dev.aurora.auditoria.model.EventoConsultado;
import dev.aurora.auditoria.model.EventoGravado;
import dev.aurora.auditoria.model.FiltroAuditoria;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Trilha do tenant atual (RLS). A tabela é somente inserção: a aplicação não tem UPDATE
 * nem DELETE nela.
 */
@Repository
public class TrilhaRepository {

	/**
	 * Unidade relacionada ao evento: a própria (recurso unidade/x), a da meta (meta/x) ou
	 * a citada nos detalhes ("unidade"). Os filtros usam o caminho dela (subárvore).
	 */
	private static final String CONSULTA = """
			SELECT e.sequencia, e.ocorrido_em, e.tipo, e.recurso, e.usuario_id, host(e.ip_origem) AS ip,
			       e.detalhes::text AS detalhes, u.nome AS usuario_nome, u.email AS usuario_email,
			       un.id AS unidade_id, un.nome AS unidade_nome,
			       CASE split_part(e.recurso, '/', 1)
			            WHEN 'usuario' THEN (SELECT x.nome FROM usuario x WHERE x.id::text = split_part(e.recurso, '/', 2))
			            WHEN 'perfil' THEN (SELECT x.nome FROM perfil x WHERE x.id::text = split_part(e.recurso, '/', 2))
			            WHEN 'unidade' THEN un.nome
			            WHEN 'meta' THEN 'Meta de ' || un.nome
			       END AS recurso_nome
			  FROM evento_auditoria e
			  LEFT JOIN usuario u ON u.id = e.usuario_id
			  LEFT JOIN LATERAL (
			       SELECT x.id, x.nome, x.caminho FROM unidade x
			        WHERE x.id::text = CASE
			              WHEN e.recurso LIKE 'unidade/%' THEN split_part(e.recurso, '/', 2)
			              WHEN e.recurso LIKE 'meta/%' THEN (SELECT m.unidade_id::text FROM meta m
			                                                  WHERE m.id::text = split_part(e.recurso, '/', 2))
			              ELSE e.detalhes ->> 'unidade' END) un ON true
			 WHERE (CAST(:usuario AS uuid) IS NULL OR e.usuario_id = CAST(:usuario AS uuid))
			   AND (cardinality(CAST(:tipos AS text[])) = 0 OR e.tipo = ANY (CAST(:tipos AS text[])))
			   AND (CAST(:de AS timestamptz) IS NULL OR e.ocorrido_em >= CAST(:de AS timestamptz))
			   AND (CAST(:ate AS timestamptz) IS NULL OR e.ocorrido_em < CAST(:ate AS timestamptz))
			   AND (CAST(:unidade AS uuid) IS NULL
			        OR un.caminho <@ (SELECT r.caminho FROM unidade r WHERE r.id = CAST(:unidade AS uuid)))
			   AND (CAST(:antes AS bigint) IS NULL OR e.sequencia < CAST(:antes AS bigint))
			 ORDER BY e.sequencia DESC""";

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

	/** Página da trilha do tenant, do mais recente para o mais antigo. */
	public List<EventoConsultado> consultar(FiltroAuditoria filtro, @Nullable Long antesDe, int limite) {
		return parametros(jdbc.sql(CONSULTA + " LIMIT :limite"), filtro, antesDe).param("limite", limite)
			.query((r, n) -> consultado(r))
			.list();
	}

	/** Todos os eventos do filtro, em fluxo (exportação). */
	public void percorrer(FiltroAuditoria filtro, Consumer<EventoConsultado> consumidor) {
		try (var eventos = parametros(jdbc.sql(CONSULTA), filtro, null).query((r, n) -> consultado(r)).stream()) {
			eventos.forEach(consumidor);
		}
	}

	/** Quem aparece na trilha (para o filtro por pessoa). */
	public List<Autor> listarAutores() {
		return jdbc.sql("""
				SELECT u.id, u.nome, u.email FROM usuario u
				 WHERE EXISTS (SELECT 1 FROM evento_auditoria e WHERE e.usuario_id = u.id)
				 ORDER BY lower(u.nome)""").query(Autor.class).list();
	}

	private static JdbcClient.StatementSpec parametros(JdbcClient.StatementSpec sql, FiltroAuditoria filtro,
			@Nullable Long antesDe) {
		return sql.param("usuario", filtro.usuarioId())
			.param("tipos", filtro.tipos().toArray(String[]::new))
			.param("de", filtro.de() == null ? null : Timestamp.from(filtro.de()))
			.param("ate", filtro.ate() == null ? null : Timestamp.from(filtro.ate()))
			.param("unidade", filtro.unidadeId())
			.param("antes", antesDe);
	}

	private static EventoConsultado consultado(ResultSet r) throws SQLException {
		return new EventoConsultado(r.getLong("sequencia"), r.getTimestamp("ocorrido_em").toInstant(),
				r.getString("tipo"), r.getString("recurso"), r.getString("recurso_nome"),
				r.getObject("usuario_id", UUID.class), r.getString("usuario_nome"), r.getString("usuario_email"),
				r.getString("ip"), r.getObject("unidade_id", UUID.class), r.getString("unidade_nome"),
				r.getString("detalhes"));
	}

}
