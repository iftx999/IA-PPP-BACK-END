package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.AmostraDePosicao;
import dev.aurora.campanhas.model.Jornada;
import dev.aurora.campanhas.model.Posicao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Jornada das equipes (J8) e posições durante a jornada (RN-27). */
@Repository
public class JornadaRepository {

	private static final String SELECAO = """
			SELECT j.id, j.equipe_id, e.nome AS equipe_nome, j.usuario_id, j.inicio, j.fim
			  FROM jornada j JOIN equipe e ON e.id = j.equipe_id
			""";

	private final JdbcClient jdbc;

	JornadaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Jornada> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE j.id = :id").param("id", id).query(JornadaRepository::mapear).optional();
	}

	/** Jornadas abertas das equipes. */
	public List<Jornada> abertas(Collection<UUID> equipes) {
		if (equipes.isEmpty()) {
			return List.of();
		}
		return jdbc.sql(SELECAO + " WHERE j.equipe_id IN (:equipes) AND j.fim IS NULL ORDER BY j.inicio")
			.param("equipes", equipes)
			.query(JornadaRepository::mapear)
			.list();
	}

	/** A jornada mais recente das equipes (aberta ou encerrada). */
	public Optional<Jornada> ultima(Collection<UUID> equipes) {
		if (equipes.isEmpty()) {
			return Optional.empty();
		}
		return jdbc.sql(SELECAO + " WHERE j.equipe_id IN (:equipes) ORDER BY j.fim IS NULL DESC, j.inicio DESC LIMIT 1")
			.param("equipes", equipes)
			.query(JornadaRepository::mapear)
			.optional();
	}

	/** Jornada da equipe que cobre o momento (aberta ou já encerrada). */
	public Optional<UUID> daEquipeEm(UUID equipe, Instant momento) {
		return jdbc.sql("""
				SELECT id FROM jornada
				 WHERE equipe_id = :equipe AND inicio <= CAST(:momento AS timestamptz) + interval '5 minutes'
				   AND (fim IS NULL OR fim >= CAST(:momento AS timestamptz) - interval '5 minutes')
				 ORDER BY inicio DESC LIMIT 1""")
			.param("equipe", equipe)
			.param("momento", Timestamp.from(momento))
			.query(UUID.class)
			.optional();
	}

	/**
	 * @return false se a equipe já tinha jornada aberta
	 */
	public boolean abrir(UUID id, UUID equipe, UUID usuario, UUID dispositivo, Instant inicio, Posicao posicao) {
		return jdbc.sql("""
				INSERT INTO jornada (id, tenant_id, equipe_id, usuario_id, dispositivo_id, inicio, posicao_inicio,
				                     precisao_inicio)
				VALUES (:id, app_tenant(), :equipe, :usuario, :dispositivo, :inicio,
				        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674), :precisao)
				ON CONFLICT DO NOTHING""")
			.param("id", id)
			.param("equipe", equipe)
			.param("usuario", usuario)
			.param("dispositivo", dispositivo)
			.param("inicio", Timestamp.from(inicio))
			.param("latitude", posicao.latitude())
			.param("longitude", posicao.longitude())
			.param("precisao", posicao.precisaoM())
			.update() == 1;
	}

	public void encerrar(UUID id, Instant fim) {
		jdbc.sql("UPDATE jornada SET fim = GREATEST(inicio, :fim) WHERE id = :id AND fim IS NULL")
			.param("id", id)
			.param("fim", Timestamp.from(fim))
			.update();
	}

	/** OS da equipe em execução (a jornada só fecha sem nenhuma). */
	public long emExecucao(UUID equipe) {
		return jdbc.sql("SELECT count(*) FROM ordem_servico WHERE equipe_id = :equipe AND status = 'EM_EXECUCAO'")
			.param("equipe", equipe)
			.query(Long.class)
			.single();
	}

	/**
	 * Grava as amostras dentro da jornada (do início até agora); a mesma amostra
	 * reenviada é ignorada.
	 * @return quantas foram gravadas
	 */
	public int registrarPosicoes(Jornada jornada, List<AmostraDePosicao> amostras, Instant agora) {
		var gravadas = 0;
		for (var a : amostras) {
			if (a.momento().isBefore(jornada.inicio()) || a.momento().isAfter(agora)
					|| (jornada.fim() != null && a.momento().isAfter(jornada.fim()))) {
				continue;
			}
			gravadas += jdbc
				.sql("""
						INSERT INTO posicao_equipe (tenant_id, jornada_id, equipe_id, momento, geom, precisao_m)
						VALUES (app_tenant(), :jornada, :equipe, :momento, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674),
						        :precisao)
						ON CONFLICT DO NOTHING""")
				.param("jornada", jornada.id())
				.param("equipe", jornada.equipeId())
				.param("momento", Timestamp.from(a.momento()))
				.param("latitude", a.posicao().latitude())
				.param("longitude", a.posicao().longitude())
				.param("precisao", a.posicao().precisaoM())
				.update();
		}
		return gravadas;
	}

	/** RN-27: apaga as posições anteriores ao corte (do tenant do contexto). */
	public int expurgarPosicoes(Instant antes) {
		return jdbc.sql("DELETE FROM posicao_equipe WHERE momento < :antes")
			.param("antes", Timestamp.from(antes))
			.update();
	}

	private static Jornada mapear(ResultSet l, int n) throws SQLException {
		var fim = l.getTimestamp("fim");
		return new Jornada(l.getObject("id", UUID.class), l.getObject("equipe_id", UUID.class),
				l.getString("equipe_nome"), l.getObject("usuario_id", UUID.class), l.getTimestamp("inicio").toInstant(),
				fim == null ? null : fim.toInstant());
	}

}
