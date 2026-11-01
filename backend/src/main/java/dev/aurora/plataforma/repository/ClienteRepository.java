package dev.aurora.plataforma.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.plataforma.model.Cliente;
import dev.aurora.plataforma.model.Limites;
import dev.aurora.plataforma.model.Logo;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cadastro dos clientes. Exige o contexto de plataforma ({@code app.plataforma}): o RLS
 * só libera tenant, tenant_emissor e assistente_limite (V23).
 */
@Repository
public class ClienteRepository {

	private static final String SELECAO = """
			SELECT t.id, t.codigo, t.nome, t.ativo, t.criado_em, t.contato_nome, t.contato_email, t.limite_usuarios,
			       t.logo IS NOT NULL AS tem_logo, t.provisionado_em, l.tokens_por_mes, l.usd_por_mes,
			       (SELECT e.emissor FROM tenant_emissor e WHERE e.tenant_id = t.id ORDER BY e.emissor LIMIT 1) AS emissor
			  FROM tenant t
			  LEFT JOIN assistente_limite l ON l.tenant_id = t.id""";

	private final JdbcClient jdbc;

	ClienteRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Cliente> listar() {
		return jdbc.sql(SELECAO + " ORDER BY lower(t.nome)").query((r, n) -> cliente(r)).list();
	}

	public Optional<Cliente> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE t.id = ?").param(id).query((r, n) -> cliente(r)).optional();
	}

	public Optional<Cliente> buscarPorCodigo(String codigo) {
		return jdbc.sql(SELECAO + " WHERE t.codigo = ?").param(codigo).query((r, n) -> cliente(r)).optional();
	}

	public Optional<Cliente> travar(UUID id) {
		jdbc.sql("SELECT id FROM tenant WHERE id = ? FOR UPDATE").param(id).query(UUID.class).optional();
		return buscar(id);
	}

	public boolean existeCodigo(String codigo) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM tenant WHERE codigo = ?)")
			.param(codigo)
			.query(Boolean.class)
			.single();
	}

	/** Emissor já usado por algum cliente (inclusive outro). */
	public Optional<UUID> clienteDoEmissor(String emissor) {
		return jdbc.sql("SELECT tenant_id FROM tenant_emissor WHERE emissor = ?")
			.param(emissor)
			.query(UUID.class)
			.optional();
	}

	public void inserir(UUID id, String codigo, String nome, String contatoNome, String contatoEmail) {
		jdbc.sql("""
				INSERT INTO tenant (id, codigo, nome, contato_nome, contato_email) VALUES (?, ?, ?, ?, ?)""")
			.params(id, codigo, nome, contatoNome, contatoEmail)
			.update();
	}

	public void inserirEmissor(UUID id, String emissor) {
		jdbc.sql("INSERT INTO tenant_emissor (emissor, tenant_id) VALUES (?, ?) ON CONFLICT (emissor) DO NOTHING")
			.params(emissor, id)
			.update();
	}

	public void atualizar(UUID id, String nome, boolean ativo, @Nullable Integer limiteUsuarios) {
		jdbc.sql("""
				UPDATE tenant SET nome = ?, ativo = ?, limite_usuarios = ?, atualizado_em = now() WHERE id = ?""")
			.params(nome, ativo, limiteUsuarios, id)
			.update();
	}

	public void definirLimitesDoAssistente(UUID id, Limites limites) {
		if (limites.assistenteTokensPorMes() == null && limites.assistenteUsdPorMes() == null) {
			jdbc.sql("DELETE FROM assistente_limite WHERE tenant_id = ?").param(id).update();
			return;
		}
		jdbc.sql("""
				INSERT INTO assistente_limite (tenant_id, tokens_por_mes, usd_por_mes) VALUES (?, ?, ?)
				ON CONFLICT (tenant_id) DO UPDATE SET tokens_por_mes = EXCLUDED.tokens_por_mes,
				                                      usd_por_mes = EXCLUDED.usd_por_mes""")
			.params(id, limites.assistenteTokensPorMes(), limites.assistenteUsdPorMes())
			.update();
	}

	public void marcarProvisionado(UUID id) {
		jdbc.sql("UPDATE tenant SET provisionado_em = now() WHERE id = ? AND provisionado_em IS NULL")
			.param(id)
			.update();
	}

	public void definirLogo(UUID id, @Nullable Logo logo) {
		jdbc.sql("UPDATE tenant SET logo = ?, logo_tipo = ?, atualizado_em = now() WHERE id = ?")
			.params(logo == null ? null : logo.conteudo(), logo == null ? null : logo.tipo(), id)
			.update();
	}

	public Optional<Logo> buscarLogo(String codigo) {
		return jdbc.sql("SELECT logo, logo_tipo FROM tenant WHERE codigo = ? AND logo IS NOT NULL AND ativo")
			.param(codigo)
			.query((r, n) -> new Logo(r.getBytes("logo"), r.getString("logo_tipo")))
			.optional();
	}

	private static Cliente cliente(ResultSet r) throws SQLException {
		var provisionado = r.getTimestamp("provisionado_em");
		return new Cliente(r.getObject("id", UUID.class), r.getString("codigo"), r.getString("nome"),
				r.getBoolean("ativo"), r.getTimestamp("criado_em").toInstant(), r.getString("emissor"),
				r.getString("contato_nome"), r.getString("contato_email"), (Integer) r.getObject("limite_usuarios"),
				(Long) r.getObject("tokens_por_mes"), r.getBigDecimal("usd_por_mes"), r.getBoolean("tem_logo"),
				provisionado == null ? null : provisionado.toInstant());
	}

}
