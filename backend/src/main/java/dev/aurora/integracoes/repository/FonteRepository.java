package dev.aurora.integracoes.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.integracoes.model.Credencial;
import dev.aurora.integracoes.model.DadosFonte;
import dev.aurora.integracoes.model.Execucao;
import dev.aurora.integracoes.model.FonteDados;
import dev.aurora.integracoes.model.TipoAdaptador;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Fontes, cargas e credenciais. {@code credencial_conector} não tem RLS (é lida pelo
 * prefixo antes de haver tenant): toda consulta aqui filtra o tenant explicitamente.
 */
@Repository
public class FonteRepository {

	private static final String SELECAO = """
			SELECT f.id, f.nome, f.tipo_adaptador, f.sistema_origem, f.frequencia,
			       EXTRACT(EPOCH FROM f.atraso_maximo)::bigint AS atraso_segundos, f.ativo, f.criado_em,
			       (SELECT max(e.fim) FROM execucao_sincronizacao e
			         WHERE e.fonte_dados_id = f.id AND e.status = 'SUCESSO') AS ultima_carga,
			       ult.status AS situacao_ultima, ult.inicio AS ultima_execucao,
			       (SELECT count(*) FROM credencial_conector c
			         WHERE c.fonte_dados_id = f.id AND c.tenant_id = app_tenant() AND c.ativo) AS credenciais
			  FROM fonte_dados f
			  LEFT JOIN LATERAL (SELECT e.status, e.inicio FROM execucao_sincronizacao e
			                      WHERE e.fonte_dados_id = f.id ORDER BY e.inicio DESC LIMIT 1) ult ON true""";

	private final JdbcClient jdbc;

	FonteRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<FonteDados> listar() {
		return jdbc.sql(SELECAO + " ORDER BY f.nome").query((r, n) -> fonte(r)).list();
	}

	public Optional<FonteDados> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE f.id = ?").param(id).query((r, n) -> fonte(r)).optional();
	}

	public void inserir(UUID id, DadosFonte d) {
		jdbc.sql("""
				INSERT INTO fonte_dados (id, tenant_id, nome, tipo_adaptador, sistema_origem, frequencia, atraso_maximo,
				                         ativo)
				VALUES (?, app_tenant(), ?, ?, ?, ?, make_interval(secs => ?), ?)""")
			.params(id, d.nome(), d.tipo().name(), d.sistemaOrigem(), d.frequencia(), d.atrasoMaximo().toSeconds(),
					d.ativo())
			.update();
	}

	public void atualizar(UUID id, DadosFonte d) {
		jdbc.sql("""
				UPDATE fonte_dados
				   SET nome = ?, tipo_adaptador = ?, sistema_origem = ?, frequencia = ?,
				       atraso_maximo = make_interval(secs => ?), ativo = ?
				 WHERE id = ?""")
			.params(d.nome(), d.tipo().name(), d.sistemaOrigem(), d.frequencia(), d.atrasoMaximo().toSeconds(),
					d.ativo(), id)
			.update();
	}

	public boolean nomeEmUso(String nome, @Nullable UUID exceto) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM fonte_dados
				                WHERE lower(nome) = lower(?) AND id IS DISTINCT FROM CAST(? AS uuid))""")
			.params(nome, exceto)
			.query(Boolean.class)
			.single();
	}

	public List<Execucao> listarExecucoes(UUID fonte, int limite) {
		return jdbc.sql("""
				SELECT id, inicio, fim, status, origem, competencia, registros_lidos, registros_gravados,
				       registros_rejeitados, registros_novos, registros_atualizados, registros_inalterados, erro
				  FROM execucao_sincronizacao WHERE fonte_dados_id = ? ORDER BY inicio DESC LIMIT ?""")
			.params(fonte, limite)
			.query((r, n) -> new Execucao(r.getObject("id", UUID.class), r.getTimestamp("inicio").toInstant(),
					instante(r.getTimestamp("fim")), r.getString("status"), r.getString("origem"),
					r.getObject("competencia", LocalDate.class), (Long) r.getObject("registros_lidos"),
					(Long) r.getObject("registros_gravados"), r.getLong("registros_rejeitados"),
					r.getLong("registros_novos"), r.getLong("registros_atualizados"),
					r.getLong("registros_inalterados"), r.getString("erro")))
			.list();
	}

	public List<Credencial> listarCredenciais(UUID fonte) {
		return jdbc.sql("""
				SELECT prefixo, criado_em, ativo FROM credencial_conector
				 WHERE fonte_dados_id = ? AND tenant_id = app_tenant() ORDER BY criado_em DESC""")
			.param(fonte)
			.query((r, n) -> new Credencial(r.getString("prefixo"), r.getTimestamp("criado_em").toInstant(),
					r.getBoolean("ativo")))
			.list();
	}

	public void inserirCredencial(String prefixo, UUID fonte, byte[] hash) {
		jdbc.sql("""
				INSERT INTO credencial_conector (prefixo, tenant_id, fonte_dados_id, hash)
				VALUES (?, app_tenant(), ?, ?)""").params(prefixo, fonte, hash).update();
	}

	/**
	 * @return id da fonte da credencial revogada, se ela era deste tenant e estava ativa
	 */
	public Optional<UUID> revogarCredencial(String prefixo) {
		return jdbc.sql("""
				UPDATE credencial_conector SET ativo = false
				 WHERE prefixo = ? AND tenant_id = app_tenant() AND ativo
				RETURNING fonte_dados_id""").param(prefixo).query(UUID.class).optional();
	}

	private static @Nullable Instant instante(@Nullable Timestamp t) {
		return t == null ? null : t.toInstant();
	}

	private static FonteDados fonte(ResultSet r) throws SQLException {
		return new FonteDados(r.getObject("id", UUID.class), r.getString("nome"),
				TipoAdaptador.valueOf(r.getString("tipo_adaptador")), r.getString("sistema_origem"),
				r.getString("frequencia"), Duration.ofSeconds(r.getLong("atraso_segundos")), r.getBoolean("ativo"),
				r.getTimestamp("criado_em").toInstant(), instante(r.getTimestamp("ultima_carga")),
				r.getString("situacao_ultima"), instante(r.getTimestamp("ultima_execucao")), r.getLong("credenciais"));
	}

}
