package dev.aurora.servicos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.servicos.model.DadosGrupo;
import dev.aurora.servicos.model.DadosHabilidade;
import dev.aurora.servicos.model.DadosMotivo;
import dev.aurora.servicos.model.DadosTipoVeiculo;
import dev.aurora.servicos.model.Grupo;
import dev.aurora.servicos.model.Habilidade;
import dev.aurora.servicos.model.Motivo;
import dev.aurora.servicos.model.PerfilRota;
import dev.aurora.servicos.model.TipoMotivo;
import dev.aurora.servicos.model.TipoVeiculo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Itens de apoio do catálogo: grupos, tipos de veículo, habilidades e motivos. O RLS
 * limita ao cliente atual.
 */
@Repository
public class CatalogoRepository {

	private final JdbcClient jdbc;

	CatalogoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Catálogo inicial do cliente atual (função da V31; não faz nada se já houver grupo).
	 */
	public void semear() {
		jdbc.sql("SELECT semear_catalogo_servicos(app_tenant())").query((linha, n) -> 0).list();
	}

	// Grupos

	public List<Grupo> grupos() {
		return jdbc.sql("""
				SELECT g.id, g.codigo, g.nome, g.ativo,
				       (SELECT count(*) FROM servico s WHERE s.grupo_id = g.id) AS servicos
				  FROM grupo_servico g ORDER BY g.ativo DESC, g.nome""")
			.query((linha, n) -> new Grupo(linha.getObject("id", UUID.class), linha.getString("codigo"),
					linha.getString("nome"), linha.getBoolean("ativo"), linha.getLong("servicos")))
			.list();
	}

	public Optional<Grupo> grupo(UUID id) {
		return grupos().stream().filter(g -> g.id().equals(id)).findFirst();
	}

	public void inserirGrupo(UUID id, DadosGrupo dados) {
		jdbc.sql(
				"INSERT INTO grupo_servico (id, tenant_id, codigo, nome, ativo) VALUES (:id, app_tenant(), :codigo, :nome, :ativo)")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ativo", dados.ativo())
			.update();
	}

	public boolean atualizarGrupo(UUID id, DadosGrupo dados) {
		return jdbc.sql("UPDATE grupo_servico SET codigo = :codigo, nome = :nome, ativo = :ativo WHERE id = :id")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ativo", dados.ativo())
			.update() == 1;
	}

	/** Serviços ativos do grupo (impedem desativá-lo). */
	public long servicosAtivosNoGrupo(UUID grupo) {
		return jdbc.sql("SELECT count(*) FROM servico WHERE grupo_id = :grupo AND ativo")
			.param("grupo", grupo)
			.query(Long.class)
			.single();
	}

	// Tipos de veículo

	public List<TipoVeiculo> veiculos() {
		return jdbc.sql("SELECT id, codigo, nome, perfil_rota, ativo FROM tipo_veiculo ORDER BY ativo DESC, nome")
			.query((linha, n) -> new TipoVeiculo(linha.getObject("id", UUID.class), linha.getString("codigo"),
					linha.getString("nome"), PerfilRota.valueOf(linha.getString("perfil_rota")),
					linha.getBoolean("ativo")))
			.list();
	}

	public void inserirVeiculo(UUID id, DadosTipoVeiculo dados) {
		jdbc.sql("""
				INSERT INTO tipo_veiculo (id, tenant_id, codigo, nome, perfil_rota, ativo)
				VALUES (:id, app_tenant(), :codigo, :nome, :perfil, :ativo)""")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("perfil", dados.perfilRota().name())
			.param("ativo", dados.ativo())
			.update();
	}

	public boolean atualizarVeiculo(UUID id, DadosTipoVeiculo dados) {
		return jdbc.sql(
				"UPDATE tipo_veiculo SET codigo = :codigo, nome = :nome, perfil_rota = :perfil, ativo = :ativo WHERE id = :id")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("perfil", dados.perfilRota().name())
			.param("ativo", dados.ativo())
			.update() == 1;
	}

	public boolean veiculoAtivo(UUID id) {
		return jdbc.sql("SELECT count(*) FROM tipo_veiculo WHERE id = :id AND ativo")
			.param("id", id)
			.query(Long.class)
			.single() > 0;
	}

	// Habilidades

	public List<Habilidade> habilidades() {
		return jdbc.sql("SELECT id, codigo, nome, ativo FROM habilidade ORDER BY ativo DESC, nome")
			.query((linha, n) -> new Habilidade(linha.getObject("id", UUID.class), linha.getString("codigo"),
					linha.getString("nome"), linha.getBoolean("ativo")))
			.list();
	}

	public void inserirHabilidade(UUID id, DadosHabilidade dados) {
		jdbc.sql(
				"INSERT INTO habilidade (id, tenant_id, codigo, nome, ativo) VALUES (:id, app_tenant(), :codigo, :nome, :ativo)")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ativo", dados.ativo())
			.update();
	}

	public boolean atualizarHabilidade(UUID id, DadosHabilidade dados) {
		return jdbc.sql("UPDATE habilidade SET codigo = :codigo, nome = :nome, ativo = :ativo WHERE id = :id")
			.param("id", id)
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("ativo", dados.ativo())
			.update() == 1;
	}

	public boolean habilidadeAtiva(UUID id) {
		return jdbc.sql("SELECT count(*) FROM habilidade WHERE id = :id AND ativo")
			.param("id", id)
			.query(Long.class)
			.single() > 0;
	}

	// Motivos

	public List<Motivo> motivos() {
		return jdbc.sql("""
				SELECT id, tipo, codigo, nome, exige_observacao, servico_id, ativo FROM motivo
				 ORDER BY tipo, ativo DESC, codigo = 'OUTRO', nome""")
			.query((linha, n) -> new Motivo(linha.getObject("id", UUID.class),
					TipoMotivo.valueOf(linha.getString("tipo")), linha.getString("codigo"), linha.getString("nome"),
					linha.getBoolean("exige_observacao"), linha.getObject("servico_id", UUID.class),
					linha.getBoolean("ativo")))
			.list();
	}

	public void inserirMotivo(UUID id, DadosMotivo dados) {
		jdbc.sql("""
				INSERT INTO motivo (id, tenant_id, tipo, codigo, nome, exige_observacao, servico_id, ativo)
				VALUES (:id, app_tenant(), :tipo, :codigo, :nome, :observacao, :servico, :ativo)""")
			.param("id", id)
			.param("tipo", dados.tipo().name())
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("observacao", dados.exigeObservacao())
			.param("servico", dados.servicoId())
			.param("ativo", dados.ativo())
			.update();
	}

	public boolean atualizarMotivo(UUID id, DadosMotivo dados) {
		return jdbc.sql("""
				UPDATE motivo SET tipo = :tipo, codigo = :codigo, nome = :nome, exige_observacao = :observacao,
				       servico_id = :servico, ativo = :ativo
				 WHERE id = :id""")
			.param("id", id)
			.param("tipo", dados.tipo().name())
			.param("codigo", dados.codigo())
			.param("nome", dados.nome())
			.param("observacao", dados.exigeObservacao())
			.param("servico", dados.servicoId())
			.param("ativo", dados.ativo())
			.update() == 1;
	}

	/** Código já usado por outro item da mesma tabela (no motivo, do mesmo tipo). */
	public boolean codigoEmUso(Tabela tabela, String codigo, UUID excetoId, TipoMotivo tipoDoMotivo) {
		var filtroTipo = tabela == Tabela.MOTIVO ? " AND tipo = :tipo" : "";
		return jdbc.sql("SELECT count(*) FROM " + tabela.nome + " WHERE codigo = :codigo AND id <> :id" + filtroTipo)
			.param("codigo", codigo)
			.param("id", excetoId)
			.param("tipo", tipoDoMotivo.name())
			.query(Long.class)
			.single() > 0;
	}

	/** Tabelas com código único (nome fixo, nunca vindo do usuário). */
	public enum Tabela {

		GRUPO("grupo_servico"), SERVICO("servico"), VEICULO("tipo_veiculo"), HABILIDADE("habilidade"), MOTIVO("motivo");

		private final String nome;

		Tabela(String nome) {
			this.nome = nome;
		}

	}

}
