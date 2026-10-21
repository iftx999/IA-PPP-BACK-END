package dev.aurora.acesso.repository;

import java.sql.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.acesso.model.Perfil;
import dev.aurora.acesso.model.Permissao;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Perfis e catálogo de permissões do tenant atual (RLS). */
@Repository
public class PerfilRepository {

	private static final String SELECAO = """
			SELECT p.id, p.nome, p.descricao, p.sistema,
			       COALESCE(array_agg(pp.permissao ORDER BY pp.permissao) FILTER (WHERE pp.permissao IS NOT NULL),
			                '{}') AS permissoes,
			       (SELECT count(DISTINCT upe.usuario_id) FROM usuario_perfil_escopo upe
			         WHERE upe.perfil_id = p.id AND (upe.valido_ate IS NULL OR upe.valido_ate > now())) AS usuarios
			  FROM perfil p
			  LEFT JOIN perfil_permissao pp ON pp.perfil_id = p.id
			""";

	private final JdbcClient jdbc;

	PerfilRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Permissao> listarCatalogo() {
		return jdbc.sql("SELECT codigo, descricao FROM permissao ORDER BY codigo").query(Permissao.class).list();
	}

	public List<Perfil> listar() {
		return jdbc.sql(SELECAO + " GROUP BY p.id ORDER BY p.sistema DESC, p.nome").query((r, n) -> perfil(r)).list();
	}

	public Optional<Perfil> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE p.id = ? GROUP BY p.id").param(id).query((r, n) -> perfil(r)).optional();
	}

	/**
	 * Trava o perfil para alteração (evita duas edições simultâneas furarem a regra do
	 * último administrador).
	 */
	public Optional<Perfil> travar(UUID id) {
		jdbc.sql("SELECT id FROM perfil WHERE id = ? FOR UPDATE").param(id).query(UUID.class).optional();
		return buscar(id);
	}

	public void inserir(UUID id, String nome, @Nullable String descricao) {
		jdbc.sql("INSERT INTO perfil (id, tenant_id, nome, descricao, sistema) VALUES (?, app_tenant(), ?, ?, false)")
			.params(id, nome, descricao)
			.update();
	}

	public void atualizar(UUID id, String nome, @Nullable String descricao) {
		jdbc.sql("UPDATE perfil SET nome = ?, descricao = ? WHERE id = ? AND NOT sistema")
			.params(nome, descricao, id)
			.update();
	}

	public void substituirPermissoes(UUID perfil, Collection<String> permissoes) {
		jdbc.sql("DELETE FROM perfil_permissao WHERE perfil_id = ?").param(perfil).update();
		for (var permissao : permissoes) {
			jdbc.sql("INSERT INTO perfil_permissao (tenant_id, perfil_id, permissao) VALUES (app_tenant(), ?, ?)")
				.params(perfil, permissao)
				.update();
		}
	}

	public void remover(UUID id) {
		jdbc.sql("DELETE FROM perfil WHERE id = ? AND NOT sistema").param(id).update();
	}

	/**
	 * Usuários ativos que continuariam administrando o tenant (usuario:gerenciar na
	 * unidade raiz) se o perfil informado passasse a ter ou não essa permissão, e sem as
	 * exclusões informadas.
	 */
	public long contarAdministradores(@Nullable UUID perfilSimulado, boolean perfilSimuladoAdministra,
			@Nullable UUID semUsuario, @Nullable UUID semConcessao) {
		return jdbc.sql("""
				SELECT count(DISTINCT u.id)
				  FROM usuario u
				  JOIN usuario_perfil_escopo upe ON upe.usuario_id = u.id
				   AND (upe.valido_ate IS NULL OR upe.valido_ate > now())
				  JOIN unidade un ON un.id = upe.unidade_id AND un.ativo AND nlevel(un.caminho) = 1
				 WHERE u.ativo
				   AND u.id IS DISTINCT FROM CAST(:semUsuario AS uuid)
				   AND upe.id IS DISTINCT FROM CAST(:semConcessao AS uuid)
				   AND CASE WHEN upe.perfil_id = CAST(:perfil AS uuid) THEN :perfilAdministra
				            ELSE EXISTS (SELECT 1 FROM perfil_permissao pp
				                          WHERE pp.perfil_id = upe.perfil_id AND pp.permissao = :permissao) END""")
			.param("semUsuario", semUsuario)
			.param("semConcessao", semConcessao)
			.param("perfil", perfilSimulado)
			.param("perfilAdministra", perfilSimuladoAdministra)
			.param("permissao", Permissao.GERIR_USUARIOS)
			.query(Long.class)
			.single();
	}

	private static Perfil perfil(java.sql.ResultSet r) throws java.sql.SQLException {
		Array permissoes = r.getArray("permissoes");
		return new Perfil(r.getObject("id", UUID.class), r.getString("nome"), r.getString("descricao"),
				r.getBoolean("sistema"), Arrays.asList((String[]) permissoes.getArray()), r.getLong("usuarios"));
	}

}
