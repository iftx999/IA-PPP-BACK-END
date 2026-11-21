package dev.aurora.operacao.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.operacao.model.EquipeResumo;
import dev.aurora.operacao.model.Fato;
import dev.aurora.operacao.model.Gatilho;
import dev.aurora.operacao.model.NovaSugestao;
import dev.aurora.operacao.model.SituacaoSugestao;
import dev.aurora.operacao.model.Sugestao;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Sugestões de reotimização (ADR 0049) e as consultas do detector que encontram os fatos
 * que as abrem.
 */
@Repository
public class SugestaoRepository {

	private static final String CAMPOS = """
			SELECT s.id, s.unidade_id, s.data, s.gatilho, s.equipe_id, e.nome AS equipe_nome, s.os_id, o.numero,
			       s.descricao, s.atraso_min, s.situacao, s.criada_em, s.decidida_em, s.roteirizacao_id
			  FROM sugestao_reotimizacao s
			  LEFT JOIN equipe e ON e.id = s.equipe_id
			  LEFT JOIN ordem_servico o ON o.id = s.os_id
			""";

	private final JdbcClient jdbc;

	SugestaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Sugestões do dia na área da unidade: abertas primeiro, depois as mais novas. */
	public List<Sugestao> doDia(UUID unidade, LocalDate data) {
		return jdbc.sql(CAMPOS + """
				WHERE s.data = :data
				  AND s.unidade_id IN (SELECT u.id FROM unidade u
				                        WHERE u.caminho <@ (SELECT a.caminho FROM unidade a WHERE a.id = :unidade))
				ORDER BY s.situacao <> 'ABERTA', s.criada_em DESC
				LIMIT 200""")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.query(SugestaoRepository::mapear)
			.list();
	}

	public int abertasDoDia(UUID unidade, LocalDate data) {
		return jdbc.sql("""
				SELECT count(*) FROM sugestao_reotimizacao s
				 WHERE s.data = :data AND s.situacao = 'ABERTA'
				   AND s.unidade_id IN (SELECT u.id FROM unidade u
				                         WHERE u.caminho <@ (SELECT a.caminho FROM unidade a WHERE a.id = :unidade))""")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.query(Integer.class)
			.single();
	}

	/** Abertas no dia em todo o escopo do usuário (o RLS limita). */
	public int abertasNoEscopo(LocalDate data) {
		return jdbc.sql("SELECT count(*) FROM sugestao_reotimizacao WHERE data = :data AND situacao = 'ABERTA'")
			.param("data", Date.valueOf(data))
			.query(Integer.class)
			.single();
	}

	public List<Sugestao> recentesNoEscopo(LocalDate data) {
		return jdbc.sql(CAMPOS + " WHERE s.data = :data AND s.situacao = 'ABERTA' ORDER BY s.criada_em DESC LIMIT 5")
			.param("data", Date.valueOf(data))
			.query(SugestaoRepository::mapear)
			.list();
	}

	/** Lê travando a linha (aceitar e ignorar não correm juntos). */
	public Optional<Sugestao> travar(UUID id) {
		return jdbc.sql(CAMPOS + " WHERE s.id = :id FOR UPDATE OF s")
			.param("id", id)
			.query(SugestaoRepository::mapear)
			.optional();
	}

	public Optional<Sugestao> buscar(UUID id) {
		return jdbc.sql(CAMPOS + " WHERE s.id = :id").param("id", id).query(SugestaoRepository::mapear).optional();
	}

	/** Abertas da mesma unidade e dia (aceitas juntas numa reotimização só). */
	public List<Sugestao> abertasDaUnidade(UUID unidade, LocalDate data) {
		return jdbc.sql(CAMPOS + " WHERE s.unidade_id = :unidade AND s.data = :data AND s.situacao = 'ABERTA'")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.query(SugestaoRepository::mapear)
			.list();
	}

	/**
	 * @return true se abriu (o mesmo fato, pela chave, não abre de novo)
	 */
	public boolean abrir(UUID tenant, NovaSugestao nova) {
		return jdbc.sql("""
				INSERT INTO sugestao_reotimizacao (id, tenant_id, unidade_id, data, gatilho, equipe_id, os_id, chave,
				                                   descricao, atraso_min)
				VALUES (:id, :tenant, :unidade, :data, :gatilho, :equipe, :os, :chave, :descricao, :atraso)
				ON CONFLICT (tenant_id, chave) DO NOTHING""")
			.param("id", UUID.randomUUID())
			.param("tenant", tenant)
			.param("unidade", nova.unidadeId())
			.param("data", Date.valueOf(nova.data()))
			.param("gatilho", nova.gatilho().name())
			.param("equipe", nova.equipeId())
			.param("os", nova.osId())
			.param("chave", nova.chave())
			.param("descricao", nova.descricao())
			.param("atraso", nova.atrasoMin())
			.update() == 1;
	}

	/** Abertas cujo fato deixou de valer (outro dia ou chave fora das vigentes). */
	public int expirar(LocalDate hoje, Collection<String> vigentes) {
		return jdbc.sql("""
				UPDATE sugestao_reotimizacao SET situacao = 'EXPIRADA'
				 WHERE situacao = 'ABERTA' AND (data < :hoje OR NOT (chave = ANY (CAST(:vigentes AS text[]))))""")
			.param("hoje", Date.valueOf(hoje))
			.param("vigentes", vigentes.toArray(String[]::new))
			.update();
	}

	public void decidir(Collection<UUID> ids, SituacaoSugestao situacao, UUID usuario, @Nullable UUID roteirizacao) {
		jdbc.sql(
				"""
						UPDATE sugestao_reotimizacao
						   SET situacao = :situacao, decidida_em = now(), decidida_por = :usuario, roteirizacao_id = :roteirizacao
						 WHERE id = ANY (CAST(:ids AS uuid[])) AND situacao = 'ABERTA'""")
			.param("ids", ids.toArray(UUID[]::new))
			.param("situacao", situacao.name())
			.param("usuario", usuario)
			.param("roteirizacao", roteirizacao)
			.update();
	}

	/**
	 * Equipes em jornada que atendem a área da OS (a unidade da equipe é a da OS ou fica
	 * acima dela), para receber uma emergencial.
	 */
	public List<UUID> equipesEmJornadaParaOrdem(UUID os) {
		return jdbc.sql("""
				SELECT DISTINCT e.id FROM ordem_servico o
				  JOIN unidade uo ON uo.id = o.unidade_id
				  JOIN equipe e ON e.ativa
				  JOIN unidade ue ON ue.id = e.unidade_id AND uo.caminho <@ ue.caminho
				  JOIN jornada j ON j.equipe_id = e.id AND j.fim IS NULL
				 WHERE o.id = :os
				 LIMIT 50""").param("os", os).query(UUID.class).list();
	}

	// ----- Fatos para o detector (hoje, no escopo do sistema do tenant) -----

	/**
	 * Emergenciais abertas de hoje (ou sem data) fora das rotas, com equipe em jornada na
	 * área. A sugestão fica na unidade da equipe mais próxima na hierarquia (a mesma das
	 * sugestões dessa equipe), para serem aceitas juntas.
	 */
	public List<Fato> emergenciais(LocalDate hoje) {
		return jdbc.sql("""
				SELECT o.id, eq.unidade_id, o.equipe_id, e.nome AS equipe_nome, o.numero, o.criada_em
				  FROM ordem_servico o
				  JOIN unidade uo ON uo.id = o.unidade_id
				  LEFT JOIN equipe e ON e.id = o.equipe_id
				  JOIN LATERAL (SELECT ej.unidade_id FROM jornada j
				                  JOIN equipe ej ON ej.id = j.equipe_id AND ej.ativa
				                  JOIN unidade ue ON ue.id = ej.unidade_id
				                 WHERE j.fim IS NULL AND uo.caminho <@ ue.caminho
				                 ORDER BY nlevel(ue.caminho) DESC LIMIT 1) eq ON true
				 WHERE o.prioridade = 'EMERGENCIAL' AND o.status IN ('PLANEJADA', 'PROGRAMADA')
				   AND (o.data_programada IS NULL OR o.data_programada = :hoje) AND o.sequencia_rota IS NULL
				 LIMIT 500""")
			.param("hoje", Date.valueOf(hoje))
			.query((l, n) -> new Fato(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
					l.getObject("equipe_id", UUID.class), l.getString("equipe_nome"), l.getObject("id", UUID.class),
					l.getLong("numero"), null, instante(l, "criada_em")))
			.list();
	}

	/** OS de hoje paralisadas desde antes do limite, com a equipe em jornada. */
	public List<Fato> paralisadas(LocalDate hoje, Instant limite) {
		return jdbc.sql("""
				SELECT ult.id AS evento_id, e.unidade_id, o.equipe_id, e.nome AS equipe_nome, o.id, o.numero,
				       m.nome AS motivo, ult.ocorrido_em
				  FROM ordem_servico o
				  JOIN equipe e ON e.id = o.equipe_id
				  JOIN LATERAL (SELECT ev.id, ev.ocorrido_em, ev.motivo_id FROM os_evento ev
				                 WHERE ev.os_id = o.id AND ev.tipo = 'PARALISAR' AND ev.situacao = 'APLICADO'
				                 ORDER BY ev.sequencia DESC LIMIT 1) ult ON true
				  LEFT JOIN motivo m ON m.id = ult.motivo_id
				 WHERE o.status = 'PARALISADA' AND o.data_programada = :hoje AND ult.ocorrido_em <= :limite
				   AND EXISTS (SELECT 1 FROM jornada j WHERE j.equipe_id = o.equipe_id AND j.fim IS NULL)
				 LIMIT 500""")
			.param("hoje", Date.valueOf(hoje))
			.param("limite", Timestamp.from(limite))
			.query(SugestaoRepository::fato)
			.list();
	}

	/**
	 * OS devolvidas hoje por equipes que seguem em jornada com OS programadas (a ordem
	 * das que sobraram pode melhorar).
	 */
	public List<Fato> devolvidas(LocalDate hoje, Instant inicioDoDia) {
		return jdbc.sql("""
				SELECT ev.id AS evento_id, e.unidade_id, ev.equipe_id, e.nome AS equipe_nome, o.id, o.numero,
				       m.nome AS motivo, ev.ocorrido_em
				  FROM os_evento ev
				  JOIN ordem_servico o ON o.id = ev.os_id
				  JOIN equipe e ON e.id = ev.equipe_id
				  LEFT JOIN motivo m ON m.id = ev.motivo_id
				 WHERE ev.tipo = 'DEVOLVER' AND ev.situacao = 'APLICADO' AND ev.ocorrido_em >= :inicio
				   AND o.status = 'DEVOLVIDA'
				   AND EXISTS (SELECT 1 FROM jornada j WHERE j.equipe_id = ev.equipe_id AND j.fim IS NULL)
				   AND EXISTS (SELECT 1 FROM ordem_servico x
				                WHERE x.equipe_id = ev.equipe_id AND x.data_programada = :hoje
				                  AND x.status = 'PROGRAMADA')
				 LIMIT 500""")
			.param("hoje", Date.valueOf(hoje))
			.param("inicio", Timestamp.from(inicioDoDia))
			.query(SugestaoRepository::fato)
			.list();
	}

	/** Unidade e nome das equipes (para as sugestões de atraso). */
	public List<EquipeResumo> equipes(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("SELECT id, unidade_id, nome FROM equipe WHERE id = ANY (CAST(:ids AS uuid[]))")
			.param("ids", ids.toArray(UUID[]::new))
			.query((l, n) -> new EquipeResumo(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
					l.getString("nome")))
			.list();
	}

	private static Fato fato(ResultSet l, int n) throws SQLException {
		return new Fato(l.getObject("evento_id", UUID.class), l.getObject("unidade_id", UUID.class),
				l.getObject("equipe_id", UUID.class), l.getString("equipe_nome"), l.getObject("id", UUID.class),
				l.getLong("numero"), l.getString("motivo"), instante(l, "ocorrido_em"));
	}

	private static Sugestao mapear(ResultSet l, int n) throws SQLException {
		var numero = l.getLong("numero");
		Long os = l.wasNull() ? null : numero;
		var atraso = l.getInt("atraso_min");
		Integer atrasoMin = l.wasNull() ? null : atraso;
		return new Sugestao(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
				l.getDate("data").toLocalDate(), Gatilho.valueOf(l.getString("gatilho")),
				l.getObject("equipe_id", UUID.class), l.getString("equipe_nome"), l.getObject("os_id", UUID.class), os,
				l.getString("descricao"), atrasoMin, SituacaoSugestao.valueOf(l.getString("situacao")),
				l.getTimestamp("criada_em").toInstant(), instante(l, "decidida_em"),
				l.getObject("roteirizacao_id", UUID.class));
	}

	private static @Nullable Instant instante(ResultSet l, String coluna) throws SQLException {
		var t = l.getTimestamp(coluna);
		return t == null ? null : t.toInstant();
	}

}
