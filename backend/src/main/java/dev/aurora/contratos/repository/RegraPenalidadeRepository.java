package dev.aurora.contratos.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import dev.aurora.contratos.model.DadosRegra;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Regras calculáveis das penalidades (ADR 0035). O RLS segue o item, que segue o contrato
 * visível; só itens ainda aprovados entram nas leituras.
 */
@Repository
public class RegraPenalidadeRepository {

	private static final String SELECAO = """
			SELECT r.item_id, i.contrato_id, c.numero AS contrato_numero, i.titulo, i.clausula, i.pagina,
			       un.id AS unidade_id, un.nome AS unidade_nome, r.formula, r.valor, r.base_valor, r.base_descricao,
			       r.contagem, r.periodicidade, r.teto, r.atualizado_em, u.nome AS atualizado_por
			  FROM regra_penalidade r
			  JOIN item_contratual i ON i.id = r.item_id AND i.status = 'APROVADO'
			  JOIN contrato c ON c.id = i.contrato_id
			  JOIN unidade un ON un.id = COALESCE(i.unidade_id, c.unidade_id)
			  LEFT JOIN usuario u ON u.id = r.atualizado_por""";

	private final JdbcClient jdbc;

	RegraPenalidadeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<RegraDePenalidade> buscar(UUID item) {
		return jdbc.sql(SELECAO + " WHERE r.item_id = ?").param(item).query(this::regra).optional();
	}

	public List<RegraDePenalidade> listarDoContrato(UUID contrato) {
		return jdbc.sql(SELECAO + " WHERE i.contrato_id = ? ORDER BY i.pagina, i.clausula NULLS LAST, i.titulo")
			.param(contrato)
			.query(this::regra)
			.list();
	}

	public List<RegraDePenalidade> listarAferidasNa(UUID unidade) {
		return jdbc.sql(SELECAO + " WHERE un.id = ? ORDER BY c.numero, i.pagina, i.clausula NULLS LAST")
			.param(unidade)
			.query(this::regra)
			.list();
	}

	public List<RegraDePenalidade> listarTodas() {
		return jdbc.sql(SELECAO + " ORDER BY un.caminho, c.numero, i.pagina, i.clausula NULLS LAST")
			.query(this::regra)
			.list();
	}

	public void salvar(UUID item, DadosRegra d, UUID autor) {
		jdbc.sql("""
				INSERT INTO regra_penalidade (tenant_id, item_id, formula, valor, base_valor, base_descricao, contagem,
				                              periodicidade, teto, atualizado_por)
				VALUES (app_tenant(), :item, :formula, :valor, :base, :descricao, :contagem, :periodicidade, :teto,
				        :autor)
				ON CONFLICT (tenant_id, item_id) DO UPDATE
				   SET formula = EXCLUDED.formula, valor = EXCLUDED.valor, base_valor = EXCLUDED.base_valor,
				       base_descricao = EXCLUDED.base_descricao, contagem = EXCLUDED.contagem,
				       periodicidade = EXCLUDED.periodicidade, teto = EXCLUDED.teto,
				       atualizado_por = EXCLUDED.atualizado_por, atualizado_em = now()""")
			.param("item", item)
			.param("formula", d.formula().name())
			.param("valor", d.valor())
			.param("base", d.baseValor())
			.param("descricao", d.baseDescricao())
			.param("contagem", d.contagem().name())
			.param("periodicidade", d.periodicidade().name())
			.param("teto", d.teto())
			.param("autor", autor)
			.update();
	}

	public boolean remover(UUID item) {
		return jdbc.sql("DELETE FROM regra_penalidade WHERE item_id = ?").param(item).update() > 0;
	}

	private RegraDePenalidade regra(ResultSet r, int linha) throws SQLException {
		return new RegraDePenalidade(r.getObject("item_id", UUID.class), r.getObject("contrato_id", UUID.class),
				r.getString("contrato_numero"), r.getString("titulo"), r.getString("clausula"), r.getInt("pagina"),
				r.getObject("unidade_id", UUID.class), r.getString("unidade_nome"),
				FormulaPenalidade.valueOf(r.getString("formula")), r.getBigDecimal("valor"),
				r.getBigDecimal("base_valor"), r.getString("base_descricao"),
				ContagemPontos.valueOf(r.getString("contagem")),
				PeriodicidadePenalidade.valueOf(r.getString("periodicidade")), r.getBigDecimal("teto"),
				r.getTimestamp("atualizado_em").toInstant(), r.getString("atualizado_por"));
	}

}
