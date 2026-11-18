package dev.aurora.servicos.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.servicos.MotivoDaOrdem;
import dev.aurora.servicos.ServicoDaOrdem;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Leituras do catálogo para as OS (ADR 0044): versão atual dos serviços e motivos. */
@Repository
public class ParaOrdemRepository {

	private static final String SERVICO = """
			SELECT s.id, v.id AS versao_id, v.versao, s.codigo, v.nome, g.nome AS grupo_nome, v.alvo, v.unidade_medida,
			       v.conferido_pela_carga, s.ativo, v.prazo_emergencial_horas, v.prazo_alta_dias, v.prazo_media_dias,
			       v.prazo_baixa_dias
			  FROM servico s
			  JOIN servico_versao v ON v.servico_id = s.id AND v.versao = s.versao_atual
			  JOIN grupo_servico g ON g.id = s.grupo_id
			""";

	private static final String MOTIVO = """
			SELECT id, tipo, codigo, nome, exige_observacao, servico_id, ativo FROM motivo""";

	private final JdbcClient jdbc;

	ParaOrdemRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<ServicoDaOrdem> servicosAtivos() {
		return jdbc.sql(SERVICO + " WHERE s.ativo ORDER BY g.nome, v.nome").query(ParaOrdemRepository::servico).list();
	}

	public Optional<ServicoDaOrdem> servico(UUID id) {
		return jdbc.sql(SERVICO + " WHERE s.id = :id").param("id", id).query(ParaOrdemRepository::servico).optional();
	}

	public Optional<ServicoDaOrdem> servico(String codigo) {
		return jdbc.sql(SERVICO + " WHERE s.codigo = :codigo")
			.param("codigo", codigo)
			.query(ParaOrdemRepository::servico)
			.optional();
	}

	public List<MotivoDaOrdem> motivosAtivos() {
		return jdbc.sql(MOTIVO + " WHERE ativo ORDER BY tipo, codigo = 'OUTRO', nome")
			.query(ParaOrdemRepository::motivo)
			.list();
	}

	public Optional<MotivoDaOrdem> motivo(UUID id) {
		return jdbc.sql(MOTIVO + " WHERE id = :id").param("id", id).query(ParaOrdemRepository::motivo).optional();
	}

	private static ServicoDaOrdem servico(ResultSet l, int n) throws SQLException {
		return new ServicoDaOrdem(l.getObject("id", UUID.class), l.getObject("versao_id", UUID.class),
				l.getInt("versao"), l.getString("codigo"), l.getString("nome"), l.getString("grupo_nome"),
				l.getString("alvo"), l.getString("unidade_medida"), l.getBoolean("conferido_pela_carga"),
				l.getBoolean("ativo"), l.getInt("prazo_emergencial_horas"), l.getInt("prazo_alta_dias"),
				l.getInt("prazo_media_dias"), l.getInt("prazo_baixa_dias"));
	}

	private static MotivoDaOrdem motivo(ResultSet l, int n) throws SQLException {
		return new MotivoDaOrdem(l.getObject("id", UUID.class), l.getString("tipo"), l.getString("codigo"),
				l.getString("nome"), l.getBoolean("exige_observacao"), l.getObject("servico_id", UUID.class),
				l.getBoolean("ativo"));
	}

}
