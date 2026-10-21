package dev.aurora.unidades.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.unidades.model.Unidade;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Unidades ativas do escopo do usuário (as atribuídas e as descendentes), via
 * app_escopos().
 */
@Repository
public class UnidadeRepository {

	private static final String SELECAO = """
			SELECT id, tipo, unidade_pai_id, codigo, nome, codigo_ibge
			  FROM unidade
			 WHERE ativo AND caminho <@ ANY (app_escopos())""";

	private final JdbcClient jdbc;

	UnidadeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Unidade> listarNoEscopo() {
		return jdbc.sql(SELECAO + " ORDER BY caminho").query(Unidade.class).list();
	}

	public Optional<Unidade> buscarNoEscopo(UUID id) {
		return jdbc.sql(SELECAO + " AND id = ?").param(id).query(Unidade.class).optional();
	}

}
