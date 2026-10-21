package dev.aurora.metas.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.metas.model.LinhaMeta;
import dev.aurora.metas.model.MetaDaUnidade;
import dev.aurora.metas.model.MetaHerdada;
import dev.aurora.metas.model.NovaMeta;
import dev.aurora.metas.model.ResumoMetas;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita de metas (somente este módulo escreve em {@code meta}) e a leitura da árvore de
 * metas. A situação usa a última medição de {@code medicao}, lida aqui como modelo de
 * leitura (ADR 0006).
 */
@Repository
public class MetaRepository {

	private final JdbcClient jdbc;

	MetaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<UUID> buscarIndicadorAdesao() {
		return jdbc.sql("SELECT id FROM indicador WHERE codigo = 'ADESAO_REDE'").query(UUID.class).optional();
	}

	/**
	 * Unidades do escopo que recebem metas de adesão (sem ETE/EEE), em ordem de árvore.
	 */
	public List<LinhaMeta> listarArvore(UUID indicador, LocalDate referencia) {
		return jdbc.sql("""
				SELECT u.id, u.nome, u.tipo, u.unidade_pai_id, nlevel(u.caminho) AS nivel,
				       p.id AS p_id, p.valor_meta AS p_valor, p.valor_alerta AS p_alerta, p.vigencia_inicio AS p_inicio,
				       p.vigencia_fim AS p_fim, p.origem AS p_origem, p.item_contratual_id IS NOT NULL AS p_contrato,
				       h.unidade_id AS h_unidade, h.unidade_nome AS h_nome, h.valor_meta AS h_valor,
				       h.valor_alerta AS h_alerta,
				       m.periodo_inicio AS m_competencia, m.numerador AS m_ligados, m.denominador AS m_com_rede
				  FROM unidade u
				  LEFT JOIN LATERAL (
				        SELECT * FROM meta
				         WHERE unidade_id = u.id AND indicador_id = :indicador
				           AND vigencia_inicio <= :referencia
				           AND (vigencia_fim IS NULL OR vigencia_fim >= :referencia)) p ON true
				  LEFT JOIN LATERAL (
				        SELECT mm.unidade_id, a.nome AS unidade_nome, mm.valor_meta, mm.valor_alerta
				          FROM meta mm JOIN unidade a ON a.id = mm.unidade_id
				         WHERE p.id IS NULL AND mm.indicador_id = :indicador
				           AND a.caminho @> u.caminho AND a.id <> u.id
				           AND mm.vigencia_inicio <= :referencia
				           AND (mm.vigencia_fim IS NULL OR mm.vigencia_fim >= :referencia)
				         ORDER BY nlevel(a.caminho) DESC LIMIT 1) h ON true
				  LEFT JOIN LATERAL (
				        SELECT periodo_inicio, numerador, denominador FROM medicao
				         WHERE unidade_id = u.id AND indicador_id = :indicador
				         ORDER BY periodo_inicio DESC LIMIT 1) m ON true
				 WHERE u.ativo AND u.caminho <@ ANY (app_escopos()) AND u.tipo NOT IN ('ETE', 'EEE')
				 ORDER BY u.caminho""")
			.param("indicador", indicador)
			.param("referencia", Date.valueOf(referencia))
			.query(MetaRepository::linha)
			.list();
	}

	public ResumoMetas resumir(UUID indicador) {
		return jdbc.sql("""
				SELECT count(*) AS cadastradas,
				       count(*) FILTER (WHERE m.item_contratual_id IS NOT NULL) AS do_contrato,
				       0 AS seguem_acima
				  FROM meta m JOIN unidade u ON u.id = m.unidade_id
				 WHERE m.indicador_id = ? AND u.caminho <@ ANY (app_escopos())""")
			.param(indicador)
			.query(ResumoMetas.class)
			.single();
	}

	public UUID inserir(UUID indicador, NovaMeta meta, @Nullable UUID definidoPor) {
		var id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO meta (id, tenant_id, indicador_id, unidade_id, vigencia_inicio, vigencia_fim, valor_meta,
				                  valor_alerta, definido_por)
				VALUES (?, app_tenant(), ?, ?, ?, ?, ?, ?, ?)""")
			.params(id, indicador, meta.unidadeId(), Date.valueOf(meta.vigenciaInicio()),
					meta.vigenciaFim() == null ? null : Date.valueOf(meta.vigenciaFim()), meta.valor(), meta.alerta(),
					definidoPor)
			.update();
		return id;
	}

	/**
	 * Meta do escopo de escrita (unidade atribuída ou descendente), travada para
	 * alteração.
	 */
	public Optional<MetaDaUnidade> buscarParaAlterar(UUID id) {
		return jdbc.sql("""
				SELECT m.id, m.valor_meta, m.valor_alerta, m.vigencia_inicio, m.vigencia_fim, m.origem,
				       m.item_contratual_id IS NOT NULL AS do_contrato
				  FROM meta m JOIN unidade u ON u.id = m.unidade_id
				 WHERE m.id = ? AND u.caminho <@ ANY (app_escopos())
				   FOR UPDATE OF m""")
			.param(id)
			.query((r, n) -> new MetaDaUnidade(r.getObject("id", UUID.class), r.getBigDecimal("valor_meta"),
					r.getBigDecimal("valor_alerta"), r.getDate("vigencia_inicio").toLocalDate(),
					data(r, "vigencia_fim"), r.getString("origem"), r.getBoolean("do_contrato")))
			.optional();
	}

	public void encerrar(UUID id, LocalDate fim) {
		jdbc.sql("UPDATE meta SET vigencia_fim = ? WHERE id = ?").params(Date.valueOf(fim), id).update();
	}

	private static LinhaMeta linha(ResultSet r, int n) throws SQLException {
		MetaDaUnidade propria = null;
		if (r.getObject("p_id") != null) {
			propria = new MetaDaUnidade(r.getObject("p_id", UUID.class), r.getBigDecimal("p_valor"),
					r.getBigDecimal("p_alerta"), r.getDate("p_inicio").toLocalDate(), data(r, "p_fim"),
					r.getString("p_origem"), r.getBoolean("p_contrato"));
		}
		MetaHerdada herdada = null;
		if (r.getObject("h_unidade") != null) {
			herdada = new MetaHerdada(r.getObject("h_unidade", UUID.class), r.getString("h_nome"),
					r.getBigDecimal("h_valor"), r.getBigDecimal("h_alerta"));
		}
		BigDecimal adesao = null;
		YearMonth competencia = null;
		if (r.getObject("m_competencia") != null && r.getBigDecimal("m_com_rede").signum() > 0) {
			adesao = r.getBigDecimal("m_ligados")
				.multiply(BigDecimal.valueOf(100))
				.divide(r.getBigDecimal("m_com_rede"), 4, java.math.RoundingMode.HALF_UP);
			competencia = YearMonth.from(r.getDate("m_competencia").toLocalDate());
		}
		return new LinhaMeta(r.getObject("id", UUID.class), r.getString("nome"), r.getString("tipo"),
				r.getObject("unidade_pai_id", UUID.class), r.getInt("nivel"), propria, herdada, adesao, competencia);
	}

	private static @Nullable LocalDate data(ResultSet r, String coluna) throws SQLException {
		var data = r.getDate(coluna);
		return data == null ? null : data.toLocalDate();
	}

}
