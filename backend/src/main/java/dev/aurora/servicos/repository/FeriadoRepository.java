package dev.aurora.servicos.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.aurora.servicos.model.Feriado;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Feriados cadastrados por unidade (P6, ADR 0044). */
@Repository
public class FeriadoRepository {

	private static final String SELECAO = """
			SELECT f.id, f.unidade_id, u.nome AS unidade_nome, f.data, f.nome
			  FROM feriado f JOIN unidade u ON u.id = f.unidade_id""";

	private final JdbcClient jdbc;

	FeriadoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Feriados do ano nas unidades visíveis, por data. */
	public List<Feriado> doAno(int ano) {
		return jdbc.sql(SELECAO + " WHERE f.data BETWEEN :inicio AND :fim ORDER BY f.data, u.nome")
			.param("inicio", Date.valueOf(LocalDate.of(ano, 1, 1)))
			.param("fim", Date.valueOf(LocalDate.of(ano, 12, 31)))
			.query((l, n) -> new Feriado(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
					l.getString("unidade_nome"), l.getDate("data").toLocalDate(), l.getString("nome"), false))
			.list();
	}

	public Optional<Feriado> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE f.id = :id")
			.param("id", id)
			.query((l, n) -> new Feriado(l.getObject("id", UUID.class), l.getObject("unidade_id", UUID.class),
					l.getString("unidade_nome"), l.getDate("data").toLocalDate(), l.getString("nome"), false))
			.optional();
	}

	public boolean existe(UUID unidade, LocalDate data) {
		return jdbc.sql("SELECT count(*) FROM feriado WHERE unidade_id = :unidade AND data = :data")
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.query(Long.class)
			.single() > 0;
	}

	public void inserir(UUID id, UUID unidade, LocalDate data, String nome, UUID autor) {
		jdbc.sql("""
				INSERT INTO feriado (id, tenant_id, unidade_id, data, nome, criado_por)
				VALUES (:id, app_tenant(), :unidade, :data, :nome, :autor)""")
			.param("id", id)
			.param("unidade", unidade)
			.param("data", Date.valueOf(data))
			.param("nome", nome)
			.param("autor", autor)
			.update();
	}

	public void remover(UUID id) {
		jdbc.sql("DELETE FROM feriado WHERE id = :id").param("id", id).update();
	}

	/**
	 * Datas de feriado da unidade e das unidades acima dela no período (o caminho
	 * {@code ltree} da unidade fica abaixo do da unidade do feriado).
	 */
	public Set<LocalDate> daUnidade(UUID unidade, LocalDate inicio, LocalDate fim) {
		return new HashSet<>(jdbc.sql("""
				SELECT f.data FROM feriado f
				 WHERE f.data BETWEEN :inicio AND :fim
				   AND (SELECT caminho FROM unidade WHERE id = :unidade)
				       <@ (SELECT caminho FROM unidade WHERE id = f.unidade_id)""")
			.param("unidade", unidade)
			.param("inicio", Date.valueOf(inicio))
			.param("fim", Date.valueOf(fim))
			.query((l, n) -> l.getDate(1).toLocalDate())
			.list());
	}

}
