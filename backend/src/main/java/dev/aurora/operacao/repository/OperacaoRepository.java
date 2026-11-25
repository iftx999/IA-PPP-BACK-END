package dev.aurora.operacao.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.operacao.model.EquipeNoDia;
import dev.aurora.operacao.model.Indicadores.ContagemPorMotivo;
import dev.aurora.operacao.model.Indicadores.PrazoDoServico;
import dev.aurora.operacao.model.Indicadores.ProdutividadeDaEquipe;
import dev.aurora.operacao.model.Indicadores.Resumo;
import dev.aurora.operacao.model.Indicadores.TempoDoServico;
import dev.aurora.operacao.model.OrdemNoDia;
import dev.aurora.operacao.model.ParadaPrevista;
import dev.aurora.operacao.model.Ponto;
import dev.aurora.operacao.model.RotaPublicada;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas do acompanhamento (J12, ADR 0049) sobre OS, eventos do app, jornadas,
 * posições e rotas. Sempre na área de uma unidade (ela e as de baixo); o RLS limita ao
 * escopo do usuário.
 */
@Repository
public class OperacaoRepository {

	/** Unidades da área: a escolhida e as de baixo. */
	private static final String AREA = """
			area AS (SELECT u.id FROM unidade u
			          WHERE u.caminho <@ (SELECT a.caminho FROM unidade a WHERE a.id = :unidade))""";

	/** Eventos que valem como o que o campo fez (inclusive os que abriram conflito). */
	private static final String VALIDOS = "('APLICADO', 'CONFLITO')";

	private final JdbcClient jdbc;

	OperacaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ----- Mapa do dia -----

	/**
	 * OS do dia na área (menos as canceladas) e, sendo hoje, as emergenciais abertas
	 * ainda sem data.
	 */
	public List<OrdemNoDia> ordensDoDia(UUID unidade, LocalDate data, boolean hoje, Instant agora) {
		return jdbc.sql("""
				WITH %s
				SELECT o.id, o.numero, v.nome AS servico_nome, o.status, o.prioridade, o.equipe_id, o.sequencia_rota,
				       ST_Y(COALESCE(l.geom, ST_LineInterpolatePoint(t.geom, 0.5))) AS lat,
				       ST_X(COALESCE(l.geom, ST_LineInterpolatePoint(t.geom, 0.5))) AS lon,
				       o.prazo_em
				  FROM ordem_servico o
				  JOIN servico_versao v ON v.id = o.servico_versao_id
				  LEFT JOIN trecho t ON t.id = o.trecho_id
				  LEFT JOIN imovel_localizacao l
				         ON o.alvo = 'IMOVEL' AND l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
				 WHERE o.unidade_id IN (SELECT id FROM area)
				   AND ((o.data_programada = :data AND o.status <> 'CANCELADA')
				        OR (:hoje AND o.prioridade = 'EMERGENCIAL' AND o.status IN ('PLANEJADA', 'PROGRAMADA')
				            AND o.data_programada IS NULL))
				 ORDER BY o.equipe_id NULLS FIRST, o.sequencia_rota NULLS LAST, o.numero
				 LIMIT 5000""".formatted(AREA))
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("hoje", hoje)
			.query((l, n) -> {
				var prazo = instante(l, "prazo_em");
				var status = l.getString("status");
				boolean aberta = !"ENCERRADA".equals(status) && !"DEVOLVIDA".equals(status);
				return new OrdemNoDia(l.getObject("id", UUID.class), l.getLong("numero"), l.getString("servico_nome"),
						status, l.getString("prioridade"), l.getObject("equipe_id", UUID.class),
						(Integer) l.getObject("sequencia_rota"), ponto(l, "lat", "lon"), prazo,
						aberta && prazo != null && prazo.isBefore(agora));
			})
			.list();
	}

	/**
	 * Equipes ativas da área ou com OS do dia na área, com a jornada do dia (a aberta ou
	 * a última) e a última posição dela.
	 */
	public List<EquipeNoDia> equipesDoDia(UUID unidade, LocalDate data, Instant inicioDoDia, Instant fimDoDia) {
		return jdbc
			.sql("""
					WITH %s
					SELECT e.id, e.nome, e.unidade_id, j.id AS jornada_id, j.inicio, j.fim,
					       COALESCE(p.lat, ST_Y(j.posicao_inicio)) AS lat, COALESCE(p.lon, ST_X(j.posicao_inicio)) AS lon,
					       CASE WHEN p.momento IS NOT NULL THEN p.momento WHEN j.posicao_inicio IS NOT NULL THEN j.inicio END
					         AS posicao_em,
					       COALESCE(p.precisao_m, j.precisao_inicio) AS precisao_m
					  FROM equipe e
					  LEFT JOIN LATERAL (
					       SELECT j.* FROM jornada j
					        WHERE j.equipe_id = e.id AND j.inicio >= :inicio AND j.inicio < :fim
					        ORDER BY j.fim IS NULL DESC, j.inicio DESC LIMIT 1) j ON true
					  LEFT JOIN LATERAL (
					       SELECT ps.momento, ST_Y(ps.geom) AS lat, ST_X(ps.geom) AS lon, ps.precisao_m
					         FROM posicao_equipe ps WHERE ps.jornada_id = j.id
					        ORDER BY ps.momento DESC LIMIT 1) p ON true
					 WHERE e.ativa
					   AND (e.unidade_id IN (SELECT id FROM area)
					        OR EXISTS (SELECT 1 FROM ordem_servico o
					                    WHERE o.equipe_id = e.id AND o.data_programada = :data
					                      AND o.unidade_id IN (SELECT id FROM area)))
					 ORDER BY e.nome
					 LIMIT 500"""
				.formatted(AREA))
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("inicio", Timestamp.from(inicioDoDia))
			.param("fim", Timestamp.from(fimDoDia))
			.query((l, n) -> new EquipeNoDia(l.getObject("id", UUID.class), l.getString("nome"),
					l.getObject("unidade_id", UUID.class), l.getObject("jornada_id", UUID.class), instante(l, "inicio"),
					instante(l, "fim"), ponto(l, "lat", "lon"), instante(l, "posicao_em"), decimal(l, "precisao_m"),
					null, null, false, null))
			.list();
	}

	/**
	 * Rota publicada mais recente de cada equipe no dia (a mesma que o app recebe), com
	 * as paradas e a situação atual das OS.
	 */
	public List<RotaPublicada> rotasPublicadas(Collection<UUID> equipes, LocalDate data) {
		if (equipes.isEmpty()) {
			return List.of();
		}
		var linhas = jdbc.sql("""
				WITH ultima AS (
				    SELECT DISTINCT ON (ro.equipe_id) ro.id, ro.equipe_id, ro.fim_previsto, ro.confirmada_em, r.origem,
				           r.publicada_em
				      FROM rota ro JOIN roteirizacao r ON r.id = ro.roteirizacao_id
				     WHERE r.data = :data AND r.situacao = 'PUBLICADA' AND ro.equipe_id = ANY (CAST(:equipes AS uuid[]))
				     ORDER BY ro.equipe_id, r.publicada_em DESC)
				SELECT u.id AS rota_id, u.equipe_id, u.fim_previsto, u.confirmada_em, u.origem, u.publicada_em,
				       p.ordem_servico_id, p.ordem, p.chegada_prevista,
				       p.saida_prevista, p.minutos_ate, o.status, o.iniciada_em,
				       CASE WHEN o.status IN ('ENCERRADA', 'DEVOLVIDA', 'CANCELADA') OR o.equipe_id <> u.equipe_id
				            THEN COALESCE(o.movimentada_em, o.atualizado_em) END AS fim_em
				  FROM ultima u
				  JOIN rota_parada p ON p.rota_id = u.id
				  JOIN ordem_servico o ON o.id = p.ordem_servico_id
				 ORDER BY u.equipe_id, p.ordem""")
			.param("data", Date.valueOf(data))
			.param("equipes", equipes.toArray(UUID[]::new))
			.query((l, n) -> new Object[] { l.getObject("rota_id", UUID.class), l.getObject("equipe_id", UUID.class),
					instante(l, "fim_previsto"), l.getString("origem"), instante(l, "publicada_em"),
					instante(l, "confirmada_em"),
					new ParadaPrevista(l.getObject("ordem_servico_id", UUID.class), l.getInt("ordem"),
							instante(l, "chegada_prevista"), instante(l, "saida_prevista"), l.getInt("minutos_ate"),
							l.getString("status"), instante(l, "iniciada_em"), instante(l, "fim_em")) })
			.list();
		var porRota = new LinkedHashMap<UUID, List<Object[]>>();
		for (var linha : linhas) {
			porRota.computeIfAbsent((UUID) linha[0], k -> new ArrayList<>()).add(linha);
		}
		var rotas = new ArrayList<RotaPublicada>();
		porRota.forEach((rota, lista) -> {
			var primeira = lista.getFirst();
			rotas.add(new RotaPublicada(rota, (UUID) primeira[1], (Instant) primeira[2], (String) primeira[3],
					(Instant) primeira[4], (Instant) primeira[5],
					lista.stream().map(o -> (ParadaPrevista) o[6]).toList()));
		});
		return rotas;
	}

	/** Equipes com jornada aberta (as que o detector acompanha) e o início dela. */
	public java.util.Map<UUID, Instant> equipesEmJornada() {
		var lista = new LinkedHashMap<UUID, Instant>();
		jdbc.sql("SELECT equipe_id, inicio FROM jornada WHERE fim IS NULL")
			.query((l, n) -> lista.put(l.getObject("equipe_id", UUID.class), l.getTimestamp("inicio").toInstant()))
			.list();
		return lista;
	}

	// ----- Indicadores -----

	public Resumo resumo(UUID unidade, LocalDate de, LocalDate ate, Instant agora) {
		return jdbc.sql("""
				WITH %s, os AS (
				    SELECT o.* FROM ordem_servico o
				     WHERE o.unidade_id IN (SELECT id FROM area) AND o.data_programada BETWEEN :de AND :ate
				       AND o.status <> 'CANCELADA')
				SELECT count(*) AS ordens,
				       count(*) FILTER (WHERE status = 'ENCERRADA' AND resultado = 'EXECUTADA') AS executadas,
				       count(*) FILTER (WHERE status = 'ENCERRADA' AND resultado = 'NAO_EXECUTADA') AS nao_executadas,
				       count(*) FILTER (WHERE status = 'DEVOLVIDA') AS devolvidas,
				       count(*) FILTER (WHERE status = 'ENCERRADA' AND resultado = 'EXECUTADA'
				                          AND movimentada_em <= prazo_em) AS no_prazo,
				       count(*) FILTER (WHERE status = 'ENCERRADA' AND resultado = 'EXECUTADA'
				                          AND movimentada_em > prazo_em) AS fora_do_prazo,
				       count(*) FILTER (WHERE status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				                          AND prazo_em < :agora) AS vencidas_abertas
				  FROM os""".formatted(AREA))
			.param("unidade", unidade)
			.param("de", Date.valueOf(de))
			.param("ate", Date.valueOf(ate))
			.param("agora", Timestamp.from(agora))
			.query((l, n) -> new Resumo(l.getInt("ordens"), l.getInt("executadas"), l.getInt("nao_executadas"),
					l.getInt("devolvidas"), l.getInt("no_prazo"), l.getInt("fora_do_prazo"),
					l.getInt("vencidas_abertas"), null, null, 0))
			.single();
	}

	/**
	 * RN-21 por serviço: executadas no prazo e fora, abertas vencidas e no prazo (só os
	 * serviços com alguma OS nessas situações).
	 */
	public List<PrazoDoServico> prazos(UUID unidade, LocalDate de, LocalDate ate, Instant agora) {
		return jdbc.sql("""
				WITH %s
				SELECT s.id AS servico_id, sv.nome AS servico,
				       count(*) FILTER (WHERE o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA'
				                          AND o.movimentada_em <= o.prazo_em) AS no_prazo,
				       count(*) FILTER (WHERE o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA'
				                          AND o.movimentada_em > o.prazo_em) AS fora_do_prazo,
				       count(*) FILTER (WHERE o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				                          AND o.prazo_em < :agora) AS vencidas_abertas,
				       count(*) FILTER (WHERE o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				                          AND o.prazo_em >= :agora) AS abertas_no_prazo
				  FROM ordem_servico o
				  JOIN servico s ON s.id = o.servico_id
				  JOIN servico_versao sv ON sv.servico_id = s.id AND sv.versao = s.versao_atual
				 WHERE o.unidade_id IN (SELECT id FROM area) AND o.data_programada BETWEEN :de AND :ate
				   AND o.status <> 'CANCELADA'
				 GROUP BY s.id, sv.nome
				HAVING count(*) FILTER (WHERE o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				                           OR (o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA')) > 0
				 ORDER BY count(*) DESC, sv.nome""".formatted(AREA))
			.param("unidade", unidade)
			.param("de", Date.valueOf(de))
			.param("ate", Date.valueOf(ate))
			.param("agora", Timestamp.from(agora))
			.query((l, n) -> new PrazoDoServico(l.getObject("servico_id", UUID.class), l.getString("servico"),
					l.getInt("no_prazo"), l.getInt("fora_do_prazo"), l.getInt("vencidas_abertas"),
					l.getInt("abertas_no_prazo")))
			.list();
	}

	/**
	 * Tempo de execução das OS executadas pelo app: soma dos trechos ativos (iniciar ou
	 * retomar até o evento seguinte), sem as paralisações, × o tempo padrão da versão da
	 * OS (por unidade de medida, multiplicado pela quantidade executada).
	 */
	public List<TempoDoServico> tempos(UUID unidade, LocalDate de, LocalDate ate) {
		return jdbc
			.sql("""
					WITH %s, ev AS (
					    SELECT e.os_id, e.tipo, e.resultado,
					           e.ocorrido_em, lead(e.ocorrido_em) OVER (PARTITION BY e.os_id ORDER BY e.sequencia) AS seguinte
					      FROM os_evento e
					      JOIN ordem_servico o ON o.id = e.os_id
					     WHERE o.unidade_id IN (SELECT id FROM area) AND o.data_programada BETWEEN :de AND :ate
					       AND o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA' AND e.situacao IN %s),
					por_os AS (
					    SELECT ev.os_id,
					           sum(EXTRACT(EPOCH FROM ev.seguinte - ev.ocorrido_em) / 60)
					             FILTER (WHERE ev.tipo IN ('INICIAR', 'RETOMAR') AND ev.seguinte IS NOT NULL) AS real_min
					      FROM ev
					     GROUP BY ev.os_id
					    HAVING bool_or(ev.tipo = 'ENCERRAR' AND ev.resultado = 'EXECUTADA')
					       AND bool_or(ev.tipo = 'INICIAR')),
					amostra AS (
					    SELECT o.servico_id, p.real_min,
					           v.tempo_padrao_min * CASE WHEN v.tempo_por_unidade
					                                     THEN COALESCE(o.quantidade_executada, o.quantidade_prevista, 1)
					                                     ELSE 1 END AS padrao_min
					      FROM por_os p
					      JOIN ordem_servico o ON o.id = p.os_id
					      JOIN servico_versao v ON v.id = o.servico_versao_id
					     WHERE p.real_min > 0)
					SELECT s.id AS servico_id, sv.nome AS servico, sv.unidade_medida, sv.tempo_por_unidade,
					       sv.tempo_padrao_min AS padrao_atual, count(*) AS amostras,
					       avg(a.real_min) AS real_medio, avg(a.padrao_min) AS padrao_medio,
					       percentile_cont(0.5) WITHIN GROUP (ORDER BY a.real_min / NULLIF(a.padrao_min, 0)) AS razao
					  FROM amostra a
					  JOIN servico s ON s.id = a.servico_id
					  JOIN servico_versao sv ON sv.servico_id = s.id AND sv.versao = s.versao_atual
					 WHERE a.padrao_min > 0
					 GROUP BY s.id, sv.nome, sv.unidade_medida, sv.tempo_por_unidade, sv.tempo_padrao_min
					 ORDER BY count(*) DESC, sv.nome"""
				.formatted(AREA, VALIDOS))
			.param("unidade", unidade)
			.param("de", Date.valueOf(de))
			.param("ate", Date.valueOf(ate))
			.query((l, n) -> new TempoDoServico(l.getObject("servico_id", UUID.class), l.getString("servico"),
					l.getString("unidade_medida"), l.getBoolean("tempo_por_unidade"), l.getInt("amostras"),
					l.getDouble("real_medio"), l.getDouble("padrao_medio"), decimal(l, "razao"),
					l.getDouble("padrao_atual"), null))
			.list();
	}

	/** Paralisações, devoluções e não execuções registradas pelo app, por motivo. */
	public List<ContagemPorMotivo> motivos(UUID unidade, LocalDate de, LocalDate ate) {
		return jdbc.sql("""
				WITH %s
				SELECT m.tipo, m.codigo, m.nome, count(*) AS quantidade
				  FROM os_evento e
				  JOIN ordem_servico o ON o.id = e.os_id
				  JOIN motivo m ON m.id = e.motivo_id
				 WHERE o.unidade_id IN (SELECT id FROM area) AND o.data_programada BETWEEN :de AND :ate
				   AND e.situacao IN %s AND e.tipo IN ('PARALISAR', 'DEVOLVER', 'ENCERRAR')
				 GROUP BY m.tipo, m.codigo, m.nome
				 ORDER BY m.tipo, count(*) DESC, m.nome""".formatted(AREA, VALIDOS))
			.param("unidade", unidade)
			.param("de", Date.valueOf(de))
			.param("ate", Date.valueOf(ate))
			.query((l, n) -> new ContagemPorMotivo(l.getString("tipo"), l.getString("codigo"), l.getString("nome"),
					l.getInt("quantidade")))
			.list();
	}

	/**
	 * Por equipe: resultados das OS do período, horas de jornada, km pelas posições (com
	 * precisão até 100 m) e deslocamento médio (do último fim na mesma jornada, ou do
	 * início dela, até o início de cada OS).
	 */
	public List<ProdutividadeDaEquipe> produtividade(UUID unidade, LocalDate de, LocalDate ate, Instant inicio,
			Instant fim, Instant agora) {
		return jdbc
			.sql("""
					WITH %s,
					os AS (
					    SELECT o.equipe_id,
					           count(*) FILTER (WHERE o.status = 'ENCERRADA' AND o.resultado = 'EXECUTADA') AS executadas,
					           count(*) FILTER (WHERE o.status = 'ENCERRADA' AND o.resultado = 'NAO_EXECUTADA') AS nao_executadas,
					           count(*) FILTER (WHERE o.status = 'DEVOLVIDA') AS devolvidas
					      FROM ordem_servico o
					     WHERE o.unidade_id IN (SELECT id FROM area) AND o.data_programada BETWEEN :de AND :ate
					       AND o.equipe_id IS NOT NULL
					     GROUP BY o.equipe_id),
					jornadas AS (
					    SELECT j.id, j.equipe_id, j.inicio, LEAST(COALESCE(j.fim, :agora), :agora) AS fim
					      FROM jornada j
					     WHERE j.inicio >= :inicio AND j.inicio < :fim AND j.equipe_id IN (SELECT equipe_id FROM os)),
					km AS (
					    SELECT jr.equipe_id,
					           sum(COALESCE((SELECT ST_Length(ST_MakeLine(p.geom ORDER BY p.momento)::geography) / 1000
					                           FROM posicao_equipe p
					                          WHERE p.jornada_id = jr.id AND (p.precisao_m IS NULL OR p.precisao_m <= 100)), 0)) AS km,
					           sum(EXTRACT(EPOCH FROM jr.fim - jr.inicio) / 3600) AS horas
					      FROM jornadas jr
					     GROUP BY jr.equipe_id),
					primeiro_inicio AS (
					    SELECT DISTINCT ON (e.os_id) e.os_id, e.jornada_id, e.ocorrido_em
					      FROM os_evento e
					      JOIN jornadas jr ON jr.id = e.jornada_id
					     WHERE e.tipo = 'INICIAR' AND e.situacao IN %2$s
					     ORDER BY e.os_id, e.sequencia),
					deslocamento AS (
					    SELECT jr.equipe_id,
					           avg(EXTRACT(EPOCH FROM i.ocorrido_em - COALESCE(
					               (SELECT max(f.ocorrido_em) FROM os_evento f
					                 WHERE f.jornada_id = i.jornada_id AND f.os_id <> i.os_id
					                   AND f.tipo IN ('ENCERRAR', 'DEVOLVER', 'PARALISAR') AND f.situacao IN %2$s
					                   AND f.ocorrido_em <= i.ocorrido_em), jr.inicio)) / 60) AS minutos
					      FROM primeiro_inicio i
					      JOIN jornadas jr ON jr.id = i.jornada_id
					     WHERE i.ocorrido_em >= jr.inicio
					     GROUP BY jr.equipe_id)
					SELECT e.id, e.nome, os.executadas, os.nao_executadas, os.devolvidas,
					       COALESCE(km.horas, 0) AS horas, COALESCE(km.km, 0) AS km, d.minutos AS deslocamento
					  FROM os
					  JOIN equipe e ON e.id = os.equipe_id
					  LEFT JOIN km ON km.equipe_id = os.equipe_id
					  LEFT JOIN deslocamento d ON d.equipe_id = os.equipe_id
					 ORDER BY os.executadas DESC, e.nome"""
				.formatted(AREA, VALIDOS))
			.param("unidade", unidade)
			.param("de", Date.valueOf(de))
			.param("ate", Date.valueOf(ate))
			.param("inicio", Timestamp.from(inicio))
			.param("fim", Timestamp.from(fim))
			.param("agora", Timestamp.from(agora))
			.query((l, n) -> {
				int executadas = l.getInt("executadas");
				double horas = l.getDouble("horas");
				double km = l.getDouble("km");
				return new ProdutividadeDaEquipe(l.getObject("id", UUID.class), l.getString("nome"), executadas,
						l.getInt("nao_executadas"), l.getInt("devolvidas"), horas,
						horas > 0 ? executadas / horas : null, km, executadas > 0 ? km / executadas : null,
						decimal(l, "deslocamento"));
			})
			.list();
	}

	// ----- Leitura -----

	private static @Nullable Instant instante(ResultSet l, String coluna) throws SQLException {
		var t = l.getTimestamp(coluna);
		return t == null ? null : t.toInstant();
	}

	private static @Nullable Double decimal(ResultSet l, String coluna) throws SQLException {
		var valor = l.getDouble(coluna);
		return l.wasNull() ? null : valor;
	}

	private static @Nullable Ponto ponto(ResultSet l, String lat, String lon) throws SQLException {
		var y = l.getDouble(lat);
		if (l.wasNull()) {
			return null;
		}
		return new Ponto(y, l.getDouble(lon));
	}

}
