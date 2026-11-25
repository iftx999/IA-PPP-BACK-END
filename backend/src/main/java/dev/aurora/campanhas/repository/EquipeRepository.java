package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.DadosEquipe;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.EquipeDoDia;
import dev.aurora.campanhas.model.ItemDoCatalogo;
import dev.aurora.campanhas.model.Membro;
import dev.aurora.campanhas.model.OperacaoDaEquipe;
import dev.aurora.campanhas.model.Pessoa;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.model.TipoEquipe;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Equipes de campo e as pessoas de cada uma, limitadas pelo RLS ao escopo. */
@Repository
public class EquipeRepository {

	private static final String SELECAO = """
			SELECT e.id, e.unidade_id, u.nome AS unidade_nome, e.nome, e.tipo, e.empresa, e.ativa,
			       ST_Y(e.base) AS base_lat, ST_X(e.base) AS base_lon, e.jornada_inicio, e.jornada_fim, e.pausa_min,
			       e.tipo_veiculo_id,
			       (SELECT count(*) FROM ordem_servico o
			         WHERE o.equipe_id = e.id AND o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')) AS em_campo
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
				INSERT INTO equipe (id, tenant_id, unidade_id, nome, tipo, empresa, ativa, base, jornada_inicio,
				                    jornada_fim, pausa_min, tipo_veiculo_id)
				VALUES (:id, app_tenant(), :unidade, :nome, :tipo, :empresa, :ativa, %s, :inicio, :fim, :pausa,
				        :veiculo)""".formatted(BASE)).paramSource(parametros(id, dados)).update();
	}

	public void atualizar(UUID id, DadosEquipe dados) {
		jdbc.sql("""
				UPDATE equipe SET unidade_id = :unidade, nome = :nome, tipo = :tipo, empresa = :empresa, ativa = :ativa,
				       base = %s, jornada_inicio = :inicio, jornada_fim = :fim, pausa_min = :pausa,
				       tipo_veiculo_id = :veiculo
				 WHERE id = :id""".formatted(BASE)).paramSource(parametros(id, dados)).update();
	}

	private static final String BASE = """
			CASE WHEN CAST(:latitude AS double precision) IS NULL THEN NULL
			     ELSE ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674) END""";

	private static MapSqlParameterSource parametros(UUID id, DadosEquipe dados) {
		var operacao = dados.operacao();
		var base = operacao.base();
		return new MapSqlParameterSource().addValue("id", id)
			.addValue("unidade", dados.unidadeId())
			.addValue("nome", dados.nome())
			.addValue("tipo", dados.tipo().name())
			.addValue("empresa", dados.empresa())
			.addValue("ativa", dados.ativa())
			.addValue("latitude", base == null ? null : base.latitude(), Types.DOUBLE)
			.addValue("longitude", base == null ? null : base.longitude(), Types.DOUBLE)
			.addValue("inicio", operacao.jornadaInicio(), Types.TIME)
			.addValue("fim", operacao.jornadaFim(), Types.TIME)
			.addValue("pausa", operacao.pausaMin())
			.addValue("veiculo", operacao.tipoVeiculoId());
	}

	public void substituirHabilidades(UUID equipe, Collection<UUID> habilidades) {
		jdbc.sql("DELETE FROM equipe_habilidade WHERE equipe_id = :equipe").param("equipe", equipe).update();
		if (habilidades.isEmpty()) {
			return;
		}
		jdbc.sql("""
				INSERT INTO equipe_habilidade (tenant_id, equipe_id, habilidade_id)
				SELECT app_tenant(), :equipe, h FROM unnest(CAST(:habilidades AS uuid[])) AS h""")
			.param("equipe", equipe)
			.param("habilidades", habilidades.toArray(UUID[]::new))
			.update();
	}

	/** Habilidades das equipes pedidas. */
	public Map<UUID, List<UUID>> habilidades(Collection<UUID> equipes) {
		if (equipes.isEmpty()) {
			return Map.of();
		}
		var porEquipe = new HashMap<UUID, List<UUID>>();
		jdbc.sql("SELECT equipe_id, habilidade_id FROM equipe_habilidade WHERE equipe_id IN (:equipes)")
			.param("equipes", equipes)
			.query((linha, n) -> Map.entry(linha.getObject("equipe_id", UUID.class),
					linha.getObject("habilidade_id", UUID.class)))
			.list()
			.forEach(e -> porEquipe.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
		return porEquipe;
	}

	/** Tipos de veículo do catálogo, ativos e inativos. */
	public List<ItemDoCatalogo> veiculos() {
		return jdbc.sql("SELECT id, codigo, nome, ativo, perfil_rota FROM tipo_veiculo ORDER BY ativo DESC, nome")
			.query((l, n) -> new ItemDoCatalogo(l.getObject("id", UUID.class), l.getString("codigo"),
					l.getString("nome"), l.getBoolean("ativo"), l.getString("perfil_rota")))
			.list();
	}

	/** Habilidades do catálogo, ativas e inativas. */
	public List<ItemDoCatalogo> habilidadesDoCatalogo() {
		return jdbc.sql("SELECT id, codigo, nome, ativo FROM habilidade ORDER BY ativo DESC, nome")
			.query((l, n) -> new ItemDoCatalogo(l.getObject("id", UUID.class), l.getString("codigo"),
					l.getString("nome"), l.getBoolean("ativo"), null))
			.list();
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

	/** Equipes ativas do usuário com o resumo das OS abertas programadas para o dia. */
	public List<EquipeDoDia> doDia(UUID usuario, LocalDate dia) {
		return jdbc.sql("""
				SELECT e.id, e.nome, tv.nome AS veiculo_nome,
				       count(o.id) AS ordens, count(o.id) FILTER (WHERE o.prioridade = 'EMERGENCIAL') AS emergenciais,
				       EXISTS (SELECT 1 FROM jornada j WHERE j.equipe_id = e.id AND j.fim IS NULL) AS jornada_aberta
				  FROM equipe_membro m
				  JOIN equipe e ON e.id = m.equipe_id
				  LEFT JOIN tipo_veiculo tv ON tv.id = e.tipo_veiculo_id
				  LEFT JOIN ordem_servico o ON o.equipe_id = e.id AND o.data_programada = :dia
				        AND o.status IN ('PROGRAMADA', 'EM_EXECUCAO', 'PARALISADA')
				 WHERE m.usuario_id = :usuario AND e.ativa
				 GROUP BY e.id, e.nome, tv.nome
				 ORDER BY e.nome""")
			.param("usuario", usuario)
			.param("dia", java.sql.Date.valueOf(dia))
			.query((l, n) -> new EquipeDoDia(l.getObject("id", UUID.class), l.getString("nome"), l.getLong("ordens"),
					l.getLong("emergenciais"), l.getString("veiculo_nome"), l.getBoolean("jornada_aberta")))
			.list();
	}

	private static Equipe mapear(ResultSet linha, int n) throws SQLException {
		var latitude = linha.getObject("base_lat", Double.class);
		var base = latitude == null ? null : new Posicao(latitude, linha.getDouble("base_lon"), null);
		var operacao = new OperacaoDaEquipe(base, linha.getObject("jornada_inicio", LocalTime.class),
				linha.getObject("jornada_fim", LocalTime.class), linha.getInt("pausa_min"),
				linha.getObject("tipo_veiculo_id", UUID.class), List.of());
		return new Equipe(linha.getObject("id", UUID.class), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), linha.getString("nome"), TipoEquipe.valueOf(linha.getString("tipo")),
				linha.getString("empresa"), linha.getBoolean("ativa"), List.of(), linha.getLong("em_campo"), operacao);
	}

}
