package dev.aurora.campanhas.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoCampanha;
import dev.aurora.campanhas.model.Campanha;
import dev.aurora.campanhas.model.Candidato;
import dev.aurora.campanhas.model.Funil;
import dev.aurora.campanhas.model.OrigemCampanha;
import dev.aurora.campanhas.model.Recorte;
import dev.aurora.campanhas.model.StatusCampanha;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Campanhas (ADR 0036): recorte dos imóveis sem ligação na situação mensal e o funil de
 * cada campanha a partir das suas OS. Tudo limitado pelo RLS ao tenant e ao escopo.
 */
@Repository
public class CampanhaRepository {

	/** Funil de uma campanha {@code c}; {@code :hoje} define as atrasadas. */
	private static final String FUNIL = """
			  CROSS JOIN LATERAL (
			        SELECT count(*) AS total,
			               count(*) FILTER (WHERE o.status <> 'CANCELADA') AS planejadas,
			               count(*) FILTER (WHERE o.enviada_em IS NOT NULL AND o.status <> 'CANCELADA') AS em_campo,
			               count(*) FILTER (WHERE o.status = 'EXECUTADA') AS executadas,
			               count(*) FILTER (WHERE o.conferencia = 'CONFIRMADA') AS confirmadas,
			               count(*) FILTER (WHERE o.conferencia = 'DIVERGENTE') AS divergentes,
			               count(*) FILTER (WHERE o.conferencia = 'PENDENTE') AS pendentes,
			               count(*) FILTER (WHERE o.status = 'RECUSADA') AS recusadas,
			               count(*) FILTER (WHERE o.status = 'INVIAVEL') AS inviaveis,
			               count(*) FILTER (WHERE o.status = 'REAGENDADA') AS reagendadas,
			               count(*) FILTER (WHERE o.status = 'CANCELADA') AS canceladas,
			               count(*) FILTER (WHERE o.status = 'PLANEJADA') AS sem_equipe,
			               count(*) FILTER (WHERE o.status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')
			                                  AND COALESCE(o.reagendada_para, o.prazo) < :hoje) AS atrasadas
			          FROM ordem_servico o
			         WHERE o.campanha_id = c.id) f
			""";

	private static final String SELECAO = """
			SELECT c.id, c.unidade_id, u.nome AS unidade_nome, c.nome, c.origem, c.criterios::text AS criterios, c.acao,
			       c.competencia, c.prazo, c.status, c.ligados_na_criacao, c.com_rede_na_criacao,
			       p.nome AS criada_por, c.criada_em, c.finalizada_em, f.*
			  FROM campanha c
			  JOIN unidade u ON u.id = c.unidade_id
			  JOIN usuario p ON p.id = c.criada_por
			""" + FUNIL;

	/** Imóveis sem ligação do recorte na competência, com a marca de OS já aberta. */
	private static final String CANDIDATOS = """
			SELECT s.codigo_origem, s.unidade_id, s.situacao,
			       EXISTS (SELECT 1 FROM ordem_servico o
			                WHERE o.tenant_id = s.tenant_id AND o.codigo_origem = s.codigo_origem
			                  AND o.status IN ('PLANEJADA', 'EM_CAMPO', 'REAGENDADA')) AS com_os_aberta
			  FROM imovel_situacao_mensal s
			  JOIN unidade u ON u.id = s.unidade_id
			 WHERE s.competencia = :competencia
			   AND s.situacao IN ('FACTIVEL', 'CORTADO')
			   AND u.caminho <@ (SELECT caminho FROM unidade WHERE id = :unidade)
			   AND (CAST(:situacao AS text) IS NULL OR s.situacao = CAST(:situacao AS text))
			   AND (CAST(:motivo AS text) IS NULL OR s.motivo_corte = CAST(:motivo AS text))
			   AND (CAST(:oeste AS float8) IS NULL OR EXISTS (
			        SELECT 1 FROM imovel_localizacao l
			         WHERE l.tenant_id = s.tenant_id AND l.codigo_origem = s.codigo_origem
			           AND l.geom && ST_MakeEnvelope(CAST(:oeste AS float8), CAST(:sul AS float8),
			                                     CAST(:leste AS float8), CAST(:norte AS float8), 4674)))
			 ORDER BY s.codigo_origem
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CampanhaRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** RN-14: competência mais recente da situação dos imóveis no escopo. */
	public Optional<YearMonth> ultimaCompetencia() {
		return jdbc.sql("SELECT max(competencia) FROM imovel_situacao_mensal")
			.query((linha, n) -> Optional.ofNullable(linha.getDate(1)))
			.single()
			.map(data -> YearMonth.from(data.toLocalDate()));
	}

	/** Tipo da unidade (para exigir setor na origem setor). */
	public Optional<String> tipoDaUnidade(UUID unidade) {
		return jdbc.sql("SELECT tipo FROM unidade WHERE id = :id").param("id", unidade).query(String.class).optional();
	}

	/** Imóveis sem ligação do recorte, por matrícula. */
	public List<Candidato> candidatos(Recorte recorte, YearMonth competencia) {
		var area = recorte.area();
		return jdbc.sql(CANDIDATOS)
			.param("competencia", inicio(competencia))
			.param("unidade", recorte.unidadeId())
			.param("situacao", recorte.situacao())
			.param("motivo", recorte.motivoCorte())
			.param("oeste", area == null ? null : area.oeste())
			.param("sul", area == null ? null : area.sul())
			.param("leste", area == null ? null : area.leste())
			.param("norte", area == null ? null : area.norte())
			.query((linha, n) -> new Candidato(linha.getString("codigo_origem"),
					linha.getObject("unidade_id", UUID.class), linha.getString("situacao"),
					linha.getBoolean("com_os_aberta")))
			.list();
	}

	public void inserir(UUID id, String nome, Recorte recorte, AcaoCampanha acao, YearMonth competencia,
			LocalDate prazo, long ligados, long comRede, UUID criadaPor) {
		jdbc.sql("""
				INSERT INTO campanha (id, tenant_id, unidade_id, nome, origem, criterios, acao, competencia, prazo,
				                      ligados_na_criacao, com_rede_na_criacao, criada_por)
				VALUES (:id, app_tenant(), :unidade, :nome, :origem, CAST(:criterios AS jsonb), :acao, :competencia,
				        :prazo, :ligados, :comRede, :criadaPor)""")
			.param("id", id)
			.param("unidade", recorte.unidadeId())
			.param("nome", nome)
			.param("origem", recorte.origem().name())
			.param("criterios", json.writeValueAsString(recorte))
			.param("acao", acao.name())
			.param("competencia", inicio(competencia))
			.param("prazo", Date.valueOf(prazo))
			.param("ligados", ligados)
			.param("comRede", comRede)
			.param("criadaPor", criadaPor)
			.update();
	}

	public List<Campanha> listar(@Nullable StatusCampanha status, LocalDate hoje) {
		return jdbc.sql(SELECAO + """
				WHERE (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
				ORDER BY c.status = 'ATIVA' DESC, c.criada_em DESC LIMIT 200""")
			.param("status", status == null ? null : status.name())
			.param("hoje", Date.valueOf(hoje))
			.query(this::mapear)
			.list();
	}

	public Optional<Campanha> buscar(UUID id, LocalDate hoje) {
		return jdbc.sql(SELECAO + " WHERE c.id = :id")
			.param("id", id)
			.param("hoje", Date.valueOf(hoje))
			.query(this::mapear)
			.optional();
	}

	/** Status atual com trava da linha (encerrar, cancelar, distribuir). */
	public Optional<StatusCampanha> travar(UUID id) {
		return jdbc.sql("SELECT status FROM campanha WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query((linha, n) -> StatusCampanha.valueOf(linha.getString(1)))
			.optional();
	}

	public void finalizar(UUID id, StatusCampanha status, UUID usuario) {
		jdbc.sql("""
				UPDATE campanha SET status = :status, finalizada_por = :usuario, finalizada_em = now()
				 WHERE id = :id AND status = 'ATIVA'""")
			.param("id", id)
			.param("status", status.name())
			.param("usuario", usuario)
			.update();
	}

	private Campanha mapear(ResultSet linha, int n) throws SQLException {
		var finalizada = linha.getTimestamp("finalizada_em");
		var funil = new Funil(linha.getLong("total"), linha.getLong("planejadas"), linha.getLong("em_campo"),
				linha.getLong("executadas"), linha.getLong("confirmadas"), linha.getLong("divergentes"),
				linha.getLong("pendentes"), linha.getLong("recusadas"), linha.getLong("inviaveis"),
				linha.getLong("reagendadas"), linha.getLong("canceladas"), linha.getLong("sem_equipe"),
				linha.getLong("atrasadas"));
		var recorte = json.readValue(linha.getString("criterios"), Recorte.class);
		return new Campanha(linha.getObject("id", UUID.class), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), linha.getString("nome"),
				new Recorte(OrigemCampanha.valueOf(linha.getString("origem")), recorte.unidadeId(), recorte.situacao(),
						recorte.motivoCorte(), recorte.area()),
				AcaoCampanha.valueOf(linha.getString("acao")),
				YearMonth.from(linha.getDate("competencia").toLocalDate()), linha.getDate("prazo").toLocalDate(),
				StatusCampanha.valueOf(linha.getString("status")), linha.getLong("ligados_na_criacao"),
				linha.getLong("com_rede_na_criacao"), linha.getString("criada_por"),
				linha.getTimestamp("criada_em").toInstant(), finalizada == null ? null : finalizada.toInstant(), funil);
	}

	static Date inicio(YearMonth competencia) {
		return Date.valueOf(competencia.atDay(1));
	}

}
