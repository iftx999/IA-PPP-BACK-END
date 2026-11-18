package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoDeCampo;
import dev.aurora.campanhas.model.Conflito;
import dev.aurora.campanhas.model.EventoDaOrdem;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.SituacaoEvento;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoConflito;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Conflitos de campo (J11; 10.9.5) e o histórico de eventos da OS. */
@Repository
public class ConflitoRepository {

	private static final String SELECAO = """
			SELECT c.id, c.os_id, o.numero, v.nome AS servico_nome, o.status, eq.nome AS equipe_nome, c.tipo,
			       c.descricao, e.tipo AS acao, ag.nome AS agente, e.ocorrido_em, c.criado_em, c.decisao,
			       d.nome AS decidido_por, c.decidido_em
			  FROM os_conflito c
			  JOIN ordem_servico o ON o.id = c.os_id
			  JOIN servico_versao v ON v.id = o.servico_versao_id
			  JOIN os_evento e ON e.id = c.evento_id
			  JOIN usuario ag ON ag.id = e.usuario_id
			  LEFT JOIN equipe eq ON eq.id = e.equipe_id
			  LEFT JOIN usuario d ON d.id = c.decidido_por
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ConflitoRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/**
	 * Abre o conflito, se a OS ainda não tem um aberto do mesmo tipo.
	 * @return true se abriu
	 */
	public boolean abrir(UUID os, UUID unidade, UUID evento, TipoConflito tipo, String descricao) {
		return jdbc.sql("""
				INSERT INTO os_conflito (id, tenant_id, os_id, unidade_id, evento_id, tipo, descricao)
				SELECT :id, app_tenant(), :os, :unidade, :evento, :tipo, :descricao
				 WHERE NOT EXISTS (SELECT 1 FROM os_conflito
				                    WHERE os_id = :os AND tipo = :tipo AND decidido_em IS NULL)""")
			.param("id", UUID.randomUUID())
			.param("os", os)
			.param("unidade", unidade)
			.param("evento", evento)
			.param("tipo", tipo.name())
			.param("descricao", descricao)
			.update() == 1;
	}

	public List<Conflito> listar(boolean abertos, int pagina, int tamanho) {
		return jdbc
			.sql(SELECAO + " WHERE (NOT :abertos OR c.decidido_em IS NULL)"
					+ " ORDER BY c.decidido_em IS NOT NULL, c.criado_em DESC LIMIT :tamanho OFFSET :inicio")
			.param("abertos", abertos)
			.param("tamanho", tamanho)
			.param("inicio", pagina * tamanho)
			.query(ConflitoRepository::mapear)
			.list();
	}

	public long contar(boolean abertos) {
		return jdbc.sql("SELECT count(*) FROM os_conflito c WHERE (NOT :abertos OR c.decidido_em IS NULL)")
			.param("abertos", abertos)
			.query(Long.class)
			.single();
	}

	public List<Conflito> daOrdem(UUID os) {
		return jdbc.sql(SELECAO + " WHERE c.os_id = :os ORDER BY c.criado_em")
			.param("os", os)
			.query(ConflitoRepository::mapear)
			.list();
	}

	public Optional<Conflito> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE c.id = :id").param("id", id).query(ConflitoRepository::mapear).optional();
	}

	/**
	 * @return false se já estava decidido
	 */
	public boolean decidir(UUID id, String decisao, UUID usuario) {
		return jdbc.sql("""
				UPDATE os_conflito SET decisao = :decisao, decidido_por = :usuario, decidido_em = now()
				 WHERE id = :id AND decidido_em IS NULL""")
			.param("id", id)
			.param("decisao", decisao)
			.param("usuario", usuario)
			.update() == 1;
	}

	/** Eventos do app na OS, na ordem da sequência. */
	public List<EventoDaOrdem> eventos(UUID os) {
		return jdbc.sql("""
				SELECT e.id, e.sequencia, e.tipo, e.situacao, e.mensagem, e.resultado, m.nome AS motivo_nome,
				       e.observacao, e.quantidade, e.valores, e.ocorrido_em, e.recebido_em, ST_Y(e.posicao) AS latitude,
				       ST_X(e.posicao) AS longitude, e.precisao_m, e.distancia_m, e.avisos, eq.nome AS equipe_nome,
				       u.nome AS usuario_nome
				  FROM os_evento e
				  JOIN equipe eq ON eq.id = e.equipe_id
				  JOIN usuario u ON u.id = e.usuario_id
				  LEFT JOIN motivo m ON m.id = e.motivo_id
				 WHERE e.os_id = :os
				 ORDER BY e.sequencia, e.recebido_em""").param("os", os).query((l, n) -> {
			var resultado = l.getString("resultado");
			var valores = l.getString("valores");
			var latitude = l.getDouble("latitude");
			var semPonto = l.wasNull();
			return new EventoDaOrdem(l.getObject("id", UUID.class), l.getInt("sequencia"),
					AcaoDeCampo.valueOf(l.getString("tipo")), SituacaoEvento.valueOf(l.getString("situacao")),
					l.getString("mensagem"), resultado == null ? null : ResultadoOrdem.valueOf(resultado),
					l.getString("motivo_nome"), l.getString("observacao"), l.getBigDecimal("quantidade"),
					valores == null ? null : json.readTree(valores), l.getTimestamp("ocorrido_em").toInstant(),
					l.getTimestamp("recebido_em").toInstant(), semPonto ? null : latitude,
					semPonto ? null : l.getDouble("longitude"), l.getBigDecimal("precisao_m"),
					l.getBigDecimal("distancia_m"), ExecucaoRepository.avisos(l.getArray("avisos")),
					l.getString("equipe_nome"), l.getString("usuario_nome"));
		}).list();
	}

	private static Conflito mapear(ResultSet l, int n) throws SQLException {
		var decidido = l.getTimestamp("decidido_em");
		return new Conflito(l.getObject("id", UUID.class), l.getObject("os_id", UUID.class), l.getLong("numero"),
				l.getString("servico_nome"), StatusOrdem.valueOf(l.getString("status")), l.getString("equipe_nome"),
				TipoConflito.valueOf(l.getString("tipo")), l.getString("descricao"),
				AcaoDeCampo.valueOf(l.getString("acao")), l.getString("agente"),
				l.getTimestamp("ocorrido_em").toInstant(), l.getTimestamp("criado_em").toInstant(),
				l.getString("decisao"), l.getString("decidido_por"), decidido == null ? null : decidido.toInstant());
	}

}
