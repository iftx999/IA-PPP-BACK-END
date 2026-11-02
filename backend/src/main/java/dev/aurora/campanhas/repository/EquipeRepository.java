package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.DadosEquipe;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.Membro;
import dev.aurora.campanhas.model.Pessoa;
import dev.aurora.campanhas.model.TipoEquipe;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Equipes de campo e as pessoas de cada uma, limitadas pelo RLS ao escopo. */
@Repository
public class EquipeRepository {

	private static final String SELECAO = """
			SELECT e.id, e.unidade_id, u.nome AS unidade_nome, e.nome, e.tipo, e.empresa, e.ativa,
			       (SELECT count(*) FROM ordem_servico o
			         WHERE o.equipe_id = e.id AND o.status IN ('EM_CAMPO', 'REAGENDADA')) AS em_campo
			  FROM equipe e
			  JOIN unidade u ON u.id = e.unidade_id
			""";

	private final JdbcClient jdbc;

	EquipeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Equipe> listar() {
		return jdbc.sql(SELECAO + " ORDER BY e.ativa DESC, e.nome").query(EquipeRepository::mapear).list();
	}

	public Optional<Equipe> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE e.id = :id").param("id", id).query(EquipeRepository::mapear).optional();
	}

	/** Trava a equipe para alterar cadastro e pessoas sem corrida. */
	public boolean travar(UUID id) {
		return jdbc.sql("SELECT id FROM equipe WHERE id = :id FOR UPDATE")
			.param("id", id)
			.query(UUID.class)
			.optional()
			.isPresent();
	}

	public boolean nomeEmUso(String nome, UUID excetoId) {
		return jdbc.sql("SELECT count(*) FROM equipe WHERE lower(nome) = lower(:nome) AND id <> :id")
			.param("nome", nome)
			.param("id", excetoId)
			.query(Long.class)
			.single() > 0;
	}

	public void inserir(UUID id, DadosEquipe dados) {
		jdbc.sql("""
				INSERT INTO equipe (id, tenant_id, unidade_id, nome, tipo, empresa, ativa)
				VALUES (:id, app_tenant(), :unidade, :nome, :tipo, :empresa, :ativa)""")
			.param("id", id)
			.param("unidade", dados.unidadeId())
			.param("nome", dados.nome())
			.param("tipo", dados.tipo().name())
			.param("empresa", dados.empresa())
			.param("ativa", dados.ativa())
			.update();
	}

	public void atualizar(UUID id, DadosEquipe dados) {
		jdbc.sql("""
				UPDATE equipe SET unidade_id = :unidade, nome = :nome, tipo = :tipo, empresa = :empresa, ativa = :ativa
				 WHERE id = :id""")
			.param("id", id)
			.param("unidade", dados.unidadeId())
			.param("nome", dados.nome())
			.param("tipo", dados.tipo().name())
			.param("empresa", dados.empresa())
			.param("ativa", dados.ativa())
			.update();
	}

	public void substituirMembros(UUID equipe, Collection<UUID> usuarios) {
		jdbc.sql("DELETE FROM equipe_membro WHERE equipe_id = :equipe").param("equipe", equipe).update();
		for (var usuario : usuarios) {
			jdbc.sql("""
					INSERT INTO equipe_membro (tenant_id, equipe_id, usuario_id)
					VALUES (app_tenant(), :equipe, :usuario)""")
				.param("equipe", equipe)
				.param("usuario", usuario)
				.update();
		}
	}

	/** Pessoas das equipes pedidas, por equipe. */
	public List<MembroDaEquipe> membros(Collection<UUID> equipes) {
		if (equipes.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT m.equipe_id, s.id, s.nome, s.email, s.ativo
				  FROM equipe_membro m
				  JOIN usuario s ON s.id = m.usuario_id
				 WHERE m.equipe_id IN (:equipes)
				 ORDER BY s.nome""")
			.param("equipes", equipes)
			.query((linha, n) -> new MembroDaEquipe(linha.getObject("equipe_id", UUID.class),
					new Membro(linha.getObject("id", UUID.class), linha.getString("nome"), linha.getString("email"),
							linha.getBoolean("ativo"))))
			.list();
	}

	public record MembroDaEquipe(UUID equipeId, Membro membro) {
	}

	/** Quantos dos usuários informados existem e estão ativos no tenant. */
	public long contarUsuariosAtivos(Collection<UUID> usuarios) {
		if (usuarios.isEmpty()) {
			return 0;
		}
		return jdbc.sql("SELECT count(*) FROM usuario WHERE ativo AND id IN (:ids)")
			.param("ids", usuarios)
			.query(Long.class)
			.single();
	}

	/** Usuários ativos do tenant pelo início do nome ou do e-mail (até 20). */
	public List<Pessoa> buscarPessoas(String busca) {
		return jdbc.sql("""
				SELECT id, nome, email FROM usuario
				 WHERE ativo AND (lower(nome) LIKE :termo OR lower(email) LIKE :termo)
				 ORDER BY nome LIMIT 20""")
			.param("termo", "%" + busca.toLowerCase(java.util.Locale.ROOT).replace("%", "").replace("_", "") + "%")
			.query((linha, n) -> new Pessoa(linha.getObject("id", UUID.class), linha.getString("nome"),
					linha.getString("email")))
			.list();
	}

	/** Equipes ativas de que o usuário faz parte. */
	public List<UUID> equipesDe(UUID usuario) {
		return jdbc.sql("""
				SELECT m.equipe_id FROM equipe_membro m JOIN equipe e ON e.id = m.equipe_id
				 WHERE m.usuario_id = :usuario AND e.ativa""").param("usuario", usuario).query(UUID.class).list();
	}

	private static Equipe mapear(ResultSet linha, int n) throws SQLException {
		return new Equipe(linha.getObject("id", UUID.class), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), linha.getString("nome"), TipoEquipe.valueOf(linha.getString("tipo")),
				linha.getString("empresa"), linha.getBoolean("ativa"), List.of(), linha.getLong("em_campo"));
	}

}
