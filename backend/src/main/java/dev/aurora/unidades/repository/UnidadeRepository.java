package dev.aurora.unidades.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.sql.ResultSet;
import java.sql.SQLException;

import dev.aurora.unidades.Unidade;
import dev.aurora.unidades.model.DadosUnidade;
import dev.aurora.unidades.model.TipoUnidade;
import dev.aurora.unidades.model.UnidadeAdministrada;

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

	/** Inclui inativas; o escopo vale do mesmo jeito. */
	private static final String SELECAO_GESTAO = """
			SELECT u.id, u.tipo, u.unidade_pai_id, u.codigo, u.nome, u.codigo_ibge, u.latitude, u.longitude, u.ativo,
			       nlevel(u.caminho) AS nivel,
			       (SELECT count(*) FROM unidade f WHERE f.unidade_pai_id = u.id AND f.ativo) AS filhas_ativas,
			       (SELECT count(*) FROM usuario_perfil_escopo upe
			         WHERE upe.unidade_id = u.id AND (upe.valido_ate IS NULL OR upe.valido_ate > now())) AS acessos
			  FROM unidade u
			 WHERE u.caminho <@ ANY (app_escopos())""";

	private final JdbcClient jdbc;

	UnidadeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Unidade> listarNoEscopo() {
		return jdbc.sql(SELECAO + " ORDER BY caminho").query(Unidade.class).list();
	}

	public Optional<Unidade> buscarPrincipalDoEscopo() {
		return jdbc.sql(SELECAO + " ORDER BY tipo = 'GRUPO', nlevel(caminho), nome LIMIT 1")
			.query(Unidade.class)
			.optional();
	}

	public Optional<Unidade> buscarNoEscopo(UUID id) {
		return jdbc.sql(SELECAO + " AND id = ?").param(id).query(Unidade.class).optional();
	}

	public Optional<String> buscarCaminhoNoEscopo(UUID id) {
		return jdbc.sql("SELECT caminho::text FROM unidade WHERE id = ? AND ativo AND caminho <@ ANY (app_escopos())")
			.param(id)
			.query(String.class)
			.optional();
	}

	public List<UnidadeAdministrada> listarParaGestao() {
		return jdbc.sql(SELECAO_GESTAO + " ORDER BY u.caminho").query((r, n) -> administrada(r)).list();
	}

	public Optional<UnidadeAdministrada> buscarParaGestao(UUID id) {
		return jdbc.sql(SELECAO_GESTAO + " AND u.id = ?").param(id).query((r, n) -> administrada(r)).optional();
	}

	/**
	 * Trava a unidade e a subárvore (mover e inativar não podem correr em paralelo com
	 * outra mudança nelas).
	 */
	public void travarSubarvore(UUID id) {
		jdbc.sql("""
				SELECT d.id FROM unidade d, unidade u
				 WHERE u.id = ? AND d.caminho <@ u.caminho
				 ORDER BY d.id FOR UPDATE OF d""").param(id).query(UUID.class).list();
	}

	/**
	 * A unidade {@code talvezDescendente} está na subárvore de {@code raiz} (ou é ela).
	 */
	public boolean estaNaSubarvore(UUID talvezDescendente, UUID raiz) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM unidade d, unidade r
				                WHERE d.id = ? AND r.id = ? AND d.caminho <@ r.caminho)""")
			.params(talvezDescendente, raiz)
			.query(Boolean.class)
			.single();
	}

	/**
	 * O caminho é feito de rótulos dos IDs ("u" + UUID sem hífens): renomear não o
	 * altera.
	 */
	public void inserir(UUID id, TipoUnidade tipo, UUID pai, DadosUnidade dados) {
		jdbc.sql(
				"""
						INSERT INTO unidade (id, tenant_id, tipo, unidade_pai_id, codigo, nome, codigo_ibge, latitude, longitude,
						                     caminho)
						SELECT :id, app_tenant(), :tipo, p.id, :codigo, :nome, :ibge, :latitude, :longitude,
						       p.caminho || text2ltree('u' || replace(CAST(:id AS text), '-', ''))
						  FROM unidade p WHERE p.id = :pai""")
			.param("id", id)
			.param("tipo", tipo.name())
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ibge", dados.codigoIbge())
			.param("latitude", dados.latitude())
			.param("longitude", dados.longitude())
			.param("pai", pai)
			.update();
	}

	public void atualizar(UUID id, DadosUnidade dados) {
		jdbc.sql("""
				UPDATE unidade SET codigo = :codigo, nome = :nome, codigo_ibge = :ibge, latitude = :latitude,
				                   longitude = :longitude, atualizado_em = now()
				 WHERE id = :id""")
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ibge", dados.codigoIbge())
			.param("latitude", dados.latitude())
			.param("longitude", dados.longitude())
			.param("id", id)
			.update();
	}

	/**
	 * Move a unidade para debaixo do novo pai, reescrevendo o caminho de toda a subárvore
	 * numa instrução só.
	 * @return quantas unidades tiveram o caminho reescrito (a própria e as descendentes)
	 */
	public int mover(UUID id, UUID novoPai) {
		jdbc.sql("UPDATE unidade SET unidade_pai_id = ?, atualizado_em = now() WHERE id = ?")
			.params(novoPai, id)
			.update();
		return jdbc.sql("""
				UPDATE unidade d
				   SET caminho = p.caminho || subpath(d.caminho, nlevel(u.caminho) - 1)
				  FROM unidade u, unidade p
				 WHERE u.id = :id AND p.id = :pai AND d.caminho <@ u.caminho""")
			.param("id", id)
			.param("pai", novoPai)
			.update();
	}

	public void definirAtiva(UUID id, boolean ativa) {
		jdbc.sql("UPDATE unidade SET ativo = ?, atualizado_em = now() WHERE id = ?").params(ativa, id).update();
	}

	private static UnidadeAdministrada administrada(ResultSet r) throws SQLException {
		return new UnidadeAdministrada(r.getObject("id", UUID.class), TipoUnidade.valueOf(r.getString("tipo")),
				r.getObject("unidade_pai_id", UUID.class), r.getString("codigo"), r.getString("nome"),
				r.getString("codigo_ibge"), r.getBigDecimal("latitude"), r.getBigDecimal("longitude"),
				r.getBoolean("ativo"), r.getInt("nivel"), r.getLong("filhas_ativas"), r.getLong("acessos"));
	}

}
