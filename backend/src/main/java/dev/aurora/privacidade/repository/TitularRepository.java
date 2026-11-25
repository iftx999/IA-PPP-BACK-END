package dev.aurora.privacidade.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.privacidade.model.RelatorioDoTitular.Localizacao;
import dev.aurora.privacidade.model.RelatorioDoTitular.OrdemDeServico;
import dev.aurora.privacidade.model.RelatorioDoTitular.SituacaoMensal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Dados de uma matrícula nas tabelas que a guardam. O RLS limita ao tenant e ao escopo de
 * quem consulta; todas as consultas usam índices que começam por (tenant_id,
 * codigo_origem).
 */
@Repository
public class TitularRepository {

	private final JdbcClient jdbc;

	TitularRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<SituacaoMensal> situacoes(String matricula) {
		return jdbc.sql("""
				SELECT s.competencia, s.situacao, s.motivo_corte, s.unidade_id, u.nome AS unidade_nome,
				       f.nome AS fonte, s.atualizado_em
				  FROM imovel_situacao_mensal s
				  JOIN unidade u ON u.id = s.unidade_id
				  JOIN fonte_dados f ON f.id = s.fonte_dados_id
				 WHERE s.tenant_id = app_tenant() AND s.codigo_origem = ?
				 ORDER BY s.competencia""")
			.param(matricula)
			.query((r, n) -> new SituacaoMensal(YearMonth.from(r.getDate("competencia").toLocalDate()),
					r.getString("situacao"), r.getString("motivo_corte"), r.getObject("unidade_id", UUID.class),
					r.getString("unidade_nome"), r.getString("fonte"), r.getTimestamp("atualizado_em").toInstant()))
			.list();
	}

	public Optional<Localizacao> localizacao(String matricula) {
		return jdbc.sql("""
				SELECT ST_X(l.geom) AS lon, ST_Y(l.geom) AS lat, l.origem, l.precisao, l.unidade_id,
				       u.nome AS unidade_nome, l.atualizado_em
				  FROM imovel_localizacao l
				  JOIN unidade u ON u.id = l.unidade_id
				 WHERE l.tenant_id = app_tenant() AND l.codigo_origem = ?""")
			.param(matricula)
			.query((r, n) -> new Localizacao(r.getDouble("lon"), r.getDouble("lat"), r.getString("origem"),
					r.getString("precisao"), r.getObject("unidade_id", UUID.class), r.getString("unidade_nome"),
					r.getTimestamp("atualizado_em").toInstant()))
			.optional();
	}

	public List<OrdemDeServico> ordens(String matricula) {
		return jdbc.sql("""
				SELECT o.id, o.numero, c.nome AS campanha, o.tipo, o.status, o.prazo, e.nome AS equipe, o.motivo,
				       o.observacao, o.resultado_em, o.criada_em
				  FROM ordem_servico o
				  JOIN campanha c ON c.id = o.campanha_id
				  LEFT JOIN equipe e ON e.id = o.equipe_id
				 WHERE o.tenant_id = app_tenant() AND o.codigo_origem = ?
				 ORDER BY o.criada_em""")
			.param(matricula)
			.query((r, n) -> {
				var resultado = r.getTimestamp("resultado_em");
				return new OrdemDeServico(r.getObject("id", UUID.class), r.getInt("numero"), r.getString("campanha"),
						r.getString("tipo"), r.getString("status"), r.getDate("prazo").toLocalDate(),
						r.getString("equipe"), r.getString("motivo"), r.getString("observacao"),
						resultado == null ? null : resultado.toInstant(), r.getTimestamp("criada_em").toInstant());
			})
			.list();
	}

}
