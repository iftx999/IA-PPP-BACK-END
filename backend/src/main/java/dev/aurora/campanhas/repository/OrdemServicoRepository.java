package dev.aurora.campanhas.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.CampanhaDivergente;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.ContagemMotivo;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Produtividade;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.model.TipoOrdem;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Ordens de serviço (ADR 0036), limitadas pelo RLS ao escopo da unidade do imóvel. A
 * conferência pela carga (RN-19) é uma única instrução idempotente.
 */
@Repository
public class OrdemServicoRepository {

	private static final String ABERTAS = "('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')";

	private static final String SELECAO = """
			SELECT o.id, o.campanha_id, c.nome AS campanha_nome, o.numero, o.codigo_origem, o.unidade_id,
			       u.nome AS unidade_nome, o.tipo, o.situacao_na_criacao, o.prioridade, o.prazo, o.equipe_id,
			       e.nome AS equipe_nome, o.enviada_em, o.status, o.motivo, o.observacao, o.reagendada_para,
			       o.resultado_em, r.nome AS resultado_por, o.conferencia, o.confirmada_em,
			       (o.status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')
			        AND COALESCE(o.reagendada_para, o.prazo) < :hoje) AS atrasada,
			       ST_Y(l.geom) AS latitude, ST_X(l.geom) AS longitude
			  FROM ordem_servico o
			  JOIN campanha c ON c.id = o.campanha_id
			  JOIN unidade u ON u.id = o.unidade_id
			  LEFT JOIN equipe e ON e.id = o.equipe_id
			  LEFT JOIN usuario r ON r.id = o.resultado_por
			  LEFT JOIN imovel_localizacao l ON l.tenant_id = o.tenant_id AND l.codigo_origem = o.codigo_origem
			""";

	/** Ordem de trabalho: prioridade, prazo (ou data reagendada), número. */
	private static final String CAMPOS_DA_ORDEM = """
			CASE o.prioridade WHEN 'ALTA' THEN 0 WHEN 'MEDIA' THEN 1 ELSE 2 END,
			COALESCE(o.reagendada_para, o.prazo), o.numero""";

	private static final String ORDEM = " ORDER BY " + CAMPOS_DA_ORDEM;

	private static final String FILTROS = """
			 WHERE o.campanha_id = :campanha
			   AND (CAST(:status AS text) IS NULL OR o.status = CAST(:status AS text))
			   AND (CAST(:equipe AS uuid) IS NULL OR o.equipe_id = CAST(:equipe AS uuid))
			   AND (CAST(:conferencia AS text) IS NULL OR o.conferencia = CAST(:conferencia AS text))
			   AND (CAST(:prioridade AS text) IS NULL OR o.prioridade = CAST(:prioridade AS text))
			   AND (NOT :atrasadas OR (o.status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')
			                           AND COALESCE(o.reagendada_para, o.prazo) < :hoje))
			""";

	private final JdbcClient jdbc;

	OrdemServicoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Uma OS por linha das listas (mesmo tamanho), numeradas a partir de 1. */
	public record NovasOrdens(List<String> matriculas, List<UUID> unidades, List<String> tipos, List<String> situacoes,
			List<String> prioridades, List<String> conferencias) {
	}

	/**
	 * Cria as OS de uma campanha numa instrução. Imóvel que ganhou OS aberta em outra
	 * campanha nesse meio tempo fica de fora (índice único das abertas).
	 * @return quantas foram criadas
	 */
	public int inserir(UUID campanha, LocalDate prazo, NovasOrdens novas) {
		var numeros = new Integer[novas.matriculas().size()];
		for (int i = 0; i < numeros.length; i++) {
			numeros[i] = i + 1;
		}
		return jdbc.sql("""
				INSERT INTO ordem_servico (id, tenant_id, campanha_id, numero, codigo_origem, unidade_id, tipo,
				                           situacao_na_criacao, prioridade, prazo, conferencia)
				SELECT gen_random_uuid(), app_tenant(), :campanha, d.numero, d.matricula, d.unidade, d.tipo, d.situacao,
				       d.prioridade, :prazo, d.conferencia
				  FROM unnest(CAST(:numeros AS int[]), CAST(:matriculas AS text[]), CAST(:unidades AS uuid[]),
				              CAST(:tipos AS text[]), CAST(:situacoes AS text[]), CAST(:prioridades AS text[]),
				              CAST(:conferencias AS text[]))
				       AS d(numero, matricula, unidade, tipo, situacao, prioridade, conferencia)
				ON CONFLICT (tenant_id, codigo_origem) WHERE status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')
				DO NOTHING""")
			.param("campanha", campanha)
			.param("prazo", Date.valueOf(prazo))
			.param("numeros", numeros)
			.param("matriculas", novas.matriculas().toArray(String[]::new))
			.param("unidades", novas.unidades().toArray(UUID[]::new))
			.param("tipos", novas.tipos().toArray(String[]::new))
			.param("situacoes", novas.situacoes().toArray(String[]::new))
			.param("prioridades", novas.prioridades().toArray(String[]::new))
			.param("conferencias", novas.conferencias().toArray(String[]::new))
			.update();
	}

	public List<OrdemServico> listar(UUID campanha, FiltroOrdens filtro, LocalDate hoje, int pagina, int tamanho) {
		return filtrar(SELECAO + FILTROS + ORDEM + " LIMIT :limite OFFSET :inicio", campanha, filtro, hoje)
			.param("limite", tamanho)
			.param("inicio", (long) pagina * tamanho)
			.query(OrdemServicoRepository::mapear)
			.list();
	}

	public long contar(UUID campanha, FiltroOrdens filtro, LocalDate hoje) {
		return filtrar("SELECT count(*) FROM ordem_servico o" + FILTROS, campanha, filtro, hoje).query(Long.class)
			.single();
	}

	private JdbcClient.StatementSpec filtrar(String sql, UUID campanha, FiltroOrdens filtro, LocalDate hoje) {
		return jdbc.sql(sql)
			.param("campanha", campanha)
			.param("status", filtro.status() == null ? null : filtro.status().name())
			.param("equipe", filtro.equipeId())
			.param("conferencia", filtro.conferencia() == null ? null : filtro.conferencia().name())
			.param("prioridade", filtro.prioridade() == null ? null : filtro.prioridade().name())
			.param("atrasadas", filtro.somenteAtrasadas())
			.param("hoje", Date.valueOf(hoje));
	}

	/**
	 * OS das equipes informadas: as em campo e as que tiveram resultado desde
	 * {@code desde}.
	 */
	public List<OrdemServico> dasEquipes(Collection<UUID> equipes, LocalDate hoje, LocalDate desde) {
		if (equipes.isEmpty()) {
			return List.of();
		}
		return jdbc.sql(SELECAO + """
				 WHERE o.equipe_id IN (:equipes)
				   AND (o.status IN ('EM_CAMPO', 'REAGENDADA')
				        OR (o.status IN ('EXECUTADA', 'RECUSADA', 'INVIAVEL') AND o.resultado_em >= :desde))
				""" + " ORDER BY o.status NOT IN ('EM_CAMPO', 'REAGENDADA'), " + CAMPOS_DA_ORDEM + " LIMIT 500")
			.param("equipes", equipes)
			.param("hoje", Date.valueOf(hoje))
			.param("desde", Date.valueOf(desde))
			.query(OrdemServicoRepository::mapear)
			.list();
	}

	public Optional<OrdemServico> buscar(UUID id, LocalDate hoje) {
		return jdbc.sql(SELECAO + " WHERE o.id = :id")
			.param("id", id)
			.param("hoje", Date.valueOf(hoje))
			.query(OrdemServicoRepository::mapear)
			.optional();
	}

	/** Trava a OS para registrar o resultado. */
	public Optional<Travada> travar(UUID id) {
		return jdbc.sql("SELECT campanha_id, equipe_id, status, tipo FROM ordem_servico WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query((linha, n) -> new Travada(linha.getObject("campanha_id", UUID.class),
					linha.getObject("equipe_id", UUID.class), StatusOrdem.valueOf(linha.getString("status")),
					TipoOrdem.valueOf(linha.getString("tipo"))))
			.optional();
	}

	public record Travada(UUID campanhaId, @Nullable UUID equipeId, StatusOrdem status, TipoOrdem tipo) {
	}

	/**
	 * Envia OS em aberto da campanha à equipe, só as de imóveis dentro da unidade onde a
	 * equipe atua. Sem lista: as planejadas, na ordem de trabalho, até {@code limite}.
	 * @return enviadas e as que ficaram fora da área da equipe
	 */
	public Envio distribuir(UUID campanha, UUID equipe, @Nullable List<UUID> ordens, @Nullable Prioridade prioridade,
			int limite) {
		var escolhidas = ordens == null ? """
				SELECT o.id FROM ordem_servico o
				 WHERE o.campanha_id = :campanha AND o.status = 'PLANEJADA'
				   AND (CAST(:prioridade AS text) IS NULL OR o.prioridade = CAST(:prioridade AS text))
				""" + ORDEM + " LIMIT :limite" : """
				SELECT o.id FROM ordem_servico o
				 WHERE o.campanha_id = :campanha AND o.status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')
				   AND o.id = ANY (CAST(:ordens AS uuid[]))""";
		var spec = jdbc
			.sql("""
					WITH escolhidas AS (%s),
					     area AS (SELECT caminho FROM unidade WHERE id = (SELECT unidade_id FROM equipe WHERE id = :equipe)),
					     enviadas AS (
					     UPDATE ordem_servico o
					        SET equipe_id = :equipe, status = CASE WHEN o.status = 'PLANEJADA' THEN 'EM_CAMPO' ELSE o.status END,
					            enviada_em = now(), atualizado_em = now()
					       FROM unidade u
					      WHERE o.id IN (SELECT id FROM escolhidas) AND u.id = o.unidade_id
					        AND u.caminho <@ (SELECT caminho FROM area)
					     RETURNING o.id)
					SELECT (SELECT count(*) FROM enviadas) AS enviadas,
					       (SELECT count(*) FROM escolhidas) - (SELECT count(*) FROM enviadas) AS fora_da_area
					"""
				.formatted(escolhidas))
			.param("campanha", campanha)
			.param("equipe", equipe);
		if (ordens == null) {
			spec = spec.param("prioridade", prioridade == null ? null : prioridade.name()).param("limite", limite);
		}
		else {
			spec = spec.param("ordens", ordens.toArray(UUID[]::new));
		}
		return spec.query((linha, n) -> new Envio(linha.getInt("enviadas"), linha.getInt("fora_da_area"))).single();
	}

	public record Envio(int enviadas, int foraDaArea) {
	}

	public void registrarResultado(UUID id, RegistroResultado registro, UUID usuario, Conferencia conferencia) {
		jdbc.sql("""
				UPDATE ordem_servico
				   SET status = :status, motivo = :motivo, observacao = :observacao, reagendada_para = :reagendada,
				       resultado_em = CASE WHEN :status = 'REAGENDADA' THEN resultado_em ELSE now() END,
				       resultado_por = :usuario, conferencia = :conferencia, confirmada_em = NULL,
				       atualizado_em = now()
				 WHERE id = :id""")
			.param("id", id)
			.param("status", registro.resultado().status().name())
			.param("motivo", registro.motivo() == null ? null : registro.motivo().name())
			.param("observacao", registro.observacao())
			.param("reagendada", registro.reagendadaPara() == null ? null : Date.valueOf(registro.reagendadaPara()))
			.param("usuario", usuario)
			.param("conferencia", conferencia.name())
			.update();
	}

	/** Cancela as OS em aberto da campanha (encerrar ou cancelar). */
	public int cancelarAbertas(UUID campanha) {
		return jdbc
			.sql("UPDATE ordem_servico SET status = 'CANCELADA', reagendada_para = NULL, atualizado_em = now()"
					+ " WHERE campanha_id = :campanha AND status IN " + ABERTAS)
			.param("campanha", campanha)
			.update();
	}

	/**
	 * RN-19: OS executada de ligação nova ou religação é confirmada pela primeira
	 * competência, a partir do mês da execução, com o imóvel ligado; fica divergente se a
	 * competência seguinte à da execução já foi carregada e ele continua sem ligação;
	 * senão segue pendente. Idempotente (também desfaz uma divergência).
	 * @param fuso fuso do mês da execução (ex.: America/Sao_Paulo)
	 * @return quantas OS mudaram de conferência
	 */
	public int conferir(String fuso) {
		return jdbc
			.sql("""
					WITH ultima AS (SELECT max(competencia) AS competencia FROM imovel_situacao_mensal),
					     alvo AS (
					     SELECT o.id, o.conferencia, o.confirmada_em,
					            date_trunc('month', o.resultado_em AT TIME ZONE :fuso)::date AS mes,
					            (SELECT min(s.competencia) FROM imovel_situacao_mensal s
					              WHERE s.tenant_id = o.tenant_id AND s.codigo_origem = o.codigo_origem
					                AND s.situacao = 'LIGADO'
					                AND s.competencia >= date_trunc('month', o.resultado_em AT TIME ZONE :fuso)::date)
					              AS ligado_em
					       FROM ordem_servico o
					      WHERE o.status = 'EXECUTADA' AND o.conferencia IN ('PENDENTE', 'DIVERGENTE')),
					     apurado AS (
					     SELECT a.id, a.ligado_em,
					            CASE WHEN a.ligado_em IS NOT NULL THEN 'CONFIRMADA'
					                 WHEN (SELECT competencia FROM ultima) >= (a.mes + interval '1 month')::date THEN 'DIVERGENTE'
					                 ELSE 'PENDENTE' END AS conferencia
					       FROM alvo a)
					UPDATE ordem_servico o
					   SET conferencia = p.conferencia, confirmada_em = p.ligado_em, atualizado_em = now()
					  FROM apurado p
					 WHERE o.id = p.id AND o.conferencia IS DISTINCT FROM p.conferencia""")
			.param("fuso", fuso)
			.update();
	}

	public List<Produtividade> produtividade(UUID campanha, LocalDate hoje) {
		return jdbc.sql("""
				SELECT e.id, e.nome, e.tipo,
				       count(*) FILTER (WHERE o.status IN ('EM_CAMPO', 'REAGENDADA')) AS em_campo,
				       count(*) FILTER (WHERE o.status = 'EXECUTADA') AS executadas,
				       count(*) FILTER (WHERE o.conferencia = 'CONFIRMADA') AS confirmadas,
				       count(*) FILTER (WHERE o.status = 'RECUSADA') AS recusadas,
				       count(*) FILTER (WHERE o.status = 'INVIAVEL') AS inviaveis,
				       count(*) FILTER (WHERE o.status IN ('EM_CAMPO', 'REAGENDADA')
				                          AND COALESCE(o.reagendada_para, o.prazo) < :hoje) AS atrasadas
				  FROM ordem_servico o
				  JOIN equipe e ON e.id = o.equipe_id
				 WHERE o.campanha_id = :campanha AND o.status <> 'CANCELADA'
				 GROUP BY e.id, e.nome, e.tipo
				 ORDER BY count(*) FILTER (WHERE o.status = 'EXECUTADA') DESC, e.nome""")
			.param("campanha", campanha)
			.param("hoje", Date.valueOf(hoje))
			.query((linha, n) -> new Produtividade(linha.getObject("id", UUID.class), linha.getString("nome"),
					TipoEquipe.valueOf(linha.getString("tipo")), linha.getLong("em_campo"), linha.getLong("executadas"),
					linha.getLong("confirmadas"), linha.getLong("recusadas"), linha.getLong("inviaveis"),
					linha.getLong("atrasadas")))
			.list();
	}

	public List<ContagemMotivo> motivos(UUID campanha) {
		return jdbc.sql("""
				SELECT status, motivo, count(*) AS quantidade FROM ordem_servico
				 WHERE campanha_id = :campanha AND motivo IS NOT NULL
				 GROUP BY status, motivo ORDER BY count(*) DESC, motivo""")
			.param("campanha", campanha)
			.query((linha, n) -> new ContagemMotivo(StatusOrdem.valueOf(linha.getString("status")),
					MotivoResultado.valueOf(linha.getString("motivo")), linha.getLong("quantidade")))
			.list();
	}

	public List<CampanhaDivergente> campanhasComDivergencia() {
		return jdbc.sql("""
				SELECT c.id, c.nome, c.unidade_id, count(*) AS divergentes
				  FROM ordem_servico o JOIN campanha c ON c.id = o.campanha_id
				 WHERE o.conferencia = 'DIVERGENTE'
				 GROUP BY c.id, c.nome, c.unidade_id
				 ORDER BY count(*) DESC, c.nome""")
			.query((linha, n) -> new CampanhaDivergente(linha.getObject("id", UUID.class), linha.getString("nome"),
					linha.getObject("unidade_id", UUID.class), linha.getLong("divergentes")))
			.list();
	}

	private static OrdemServico mapear(ResultSet linha, int n) throws SQLException {
		var enviada = linha.getTimestamp("enviada_em");
		var resultado = linha.getTimestamp("resultado_em");
		var reagendada = linha.getDate("reagendada_para");
		var confirmada = linha.getDate("confirmada_em");
		var motivo = linha.getString("motivo");
		var latitude = linha.getDouble("latitude");
		var semPonto = linha.wasNull();
		var longitude = linha.getDouble("longitude");
		return new OrdemServico(linha.getObject("id", UUID.class), linha.getObject("campanha_id", UUID.class),
				linha.getString("campanha_nome"), linha.getInt("numero"), linha.getString("codigo_origem"),
				linha.getObject("unidade_id", UUID.class), linha.getString("unidade_nome"),
				TipoOrdem.valueOf(linha.getString("tipo")), linha.getString("situacao_na_criacao"),
				Prioridade.valueOf(linha.getString("prioridade")), linha.getDate("prazo").toLocalDate(),
				linha.getObject("equipe_id", UUID.class), linha.getString("equipe_nome"),
				enviada == null ? null : enviada.toInstant(), StatusOrdem.valueOf(linha.getString("status")),
				motivo == null ? null : MotivoResultado.valueOf(motivo), linha.getString("observacao"),
				reagendada == null ? null : reagendada.toLocalDate(), resultado == null ? null : resultado.toInstant(),
				linha.getString("resultado_por"), Conferencia.valueOf(linha.getString("conferencia")),
				confirmada == null ? null : YearMonth.from(confirmada.toLocalDate()), linha.getBoolean("atrasada"),
				semPonto ? null : latitude, semPonto ? null : longitude);
	}

}
