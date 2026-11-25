package dev.aurora.logradouros.repository;

import java.util.List;
import java.util.UUID;

import dev.aurora.logradouros.model.Importacao;
import dev.aurora.logradouros.model.ResultadoImportacao;
import dev.aurora.logradouros.model.TipoImportacao;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Histórico das importações de logradouros e endereços. */
@Repository
public class ImportacaoRepository {

	private final JdbcClient jdbc;

	ImportacaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void registrar(UUID id, ResultadoImportacao r, @Nullable UUID unidade, String arquivo, String sha256,
			UUID usuario) {
		jdbc.sql("""
				INSERT INTO importacao_logradouro (id, tenant_id, tipo, unidade_id, arquivo, sha256, lidos, novos,
				                                   atualizados, rejeitados, feita_por)
				VALUES (:id, app_tenant(), :tipo, :unidade, :arquivo, :sha, :lidos, :novos, :atualizados, :rejeitados,
				        :usuario)""")
			.param("id", id)
			.param("tipo", r.tipo().name())
			.param("unidade", unidade)
			.param("arquivo", arquivo)
			.param("sha", sha256)
			.param("lidos", r.lidos())
			.param("novos", r.novos())
			.param("atualizados", r.atualizados())
			.param("rejeitados", r.rejeitados())
			.param("usuario", usuario)
			.update();
	}

	/** As mais recentes primeiro. */
	public List<Importacao> recentes(int limite) {
		return jdbc.sql("""
				SELECT i.id, i.tipo, u.nome AS unidade, i.arquivo, i.lidos, i.novos, i.atualizados, i.rejeitados,
				       s.nome AS usuario, i.feita_em
				  FROM importacao_logradouro i
				  LEFT JOIN unidade u ON u.id = i.unidade_id
				  JOIN usuario s ON s.id = i.feita_por
				 ORDER BY i.feita_em DESC LIMIT :limite""")
			.param("limite", limite)
			.query((r, n) -> new Importacao(r.getObject("id", UUID.class), TipoImportacao.valueOf(r.getString("tipo")),
					r.getString("unidade"), r.getString("arquivo"), r.getInt("lidos"), r.getInt("novos"),
					r.getInt("atualizados"), r.getInt("rejeitados"), r.getString("usuario"),
					r.getTimestamp("feita_em").toInstant()))
			.list();
	}

}
