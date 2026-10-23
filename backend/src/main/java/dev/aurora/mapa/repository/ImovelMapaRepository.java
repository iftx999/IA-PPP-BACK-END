package dev.aurora.mapa.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.FiltroImoveis;
import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.model.Precisao;
import dev.aurora.mapa.model.SituacaoNoMapa;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Imóveis localizados com a situação da competência ({@code imovel_localizacao} ×
 * {@code imovel_situacao_mensal}, ambas com RLS por escopo).
 */
@Repository
public class ImovelMapaRepository {

	private static final String SELECAO = """
			SELECT l.codigo_origem, u.id AS unidade_id, u.nome AS unidade_nome, u.caminho::text AS caminho,
			       s.situacao, l.precisao, ST_X(l.geom) AS longitude, ST_Y(l.geom) AS latitude, l.atualizado_em
			  FROM imovel_localizacao l
			  JOIN imovel_situacao_mensal s ON s.tenant_id = l.tenant_id AND s.codigo_origem = l.codigo_origem
			                               AND s.competencia = :competencia
			  JOIN unidade u ON u.id = s.unidade_id
			""";

	private static final String DO_FILTRO = """
			   AND s.situacao = ANY (:situacoes)
			   AND (:aproximadas OR l.precisao = 'ENDERECO')
			""";

	private final JdbcClient jdbc;

	ImovelMapaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Imóveis a até {@code raio} metros do ponto, do mais próximo ao mais distante (o
	 * clique no mapa).
	 */
	public List<ImovelNoMapa> buscarPerto(double longitude, double latitude, int raio, FiltroImoveis filtro,
			int limite) {
		return jdbc.sql(SELECAO + """
				 CROSS JOIN (SELECT ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674) AS ponto) c
				 WHERE l.geom && ST_Expand(c.ponto, :raio / 50000.0)
				   AND ST_DWithin(l.geom::geography, c.ponto::geography, :raio)
				""" + DO_FILTRO + """
				ORDER BY l.geom <-> c.ponto, l.codigo_origem
				LIMIT :limite""")
			.param("competencia", CamadasRepository.inicio(filtro.competencia()))
			.param("longitude", longitude)
			.param("latitude", latitude)
			.param("raio", raio)
			.param("situacoes", filtro.situacoesNoBanco())
			.param("aproximadas", filtro.aproximadas())
			.param("limite", limite)
			.query(ImovelMapaRepository::mapear)
			.list();
	}

	public long contar(Area area, FiltroImoveis filtro) {
		return emArea("SELECT count(*) FROM (" + SELECAO + " WHERE l.geom && " + envelope() + DO_FILTRO + ") x", area,
				filtro)
			.query(Long.class)
			.single();
	}

	/** Imóveis da área em fluxo, por unidade e matrícula (exportação). */
	public void percorrer(Area area, FiltroImoveis filtro, Consumer<ImovelNoMapa> consumidor) {
		var sql = SELECAO + " WHERE l.geom && " + envelope() + DO_FILTRO + " ORDER BY u.nome, l.codigo_origem";
		try (var registros = emArea(sql, area, filtro).query(ImovelMapaRepository::mapear).stream()) {
			registros.forEach(consumidor);
		}
	}

	private JdbcClient.StatementSpec emArea(String sql, Area area, FiltroImoveis filtro) {
		return jdbc.sql(sql)
			.param("competencia", CamadasRepository.inicio(filtro.competencia()))
			.param("oeste", area.oeste())
			.param("sul", area.sul())
			.param("leste", area.leste())
			.param("norte", area.norte())
			.param("situacoes", filtro.situacoesNoBanco())
			.param("aproximadas", filtro.aproximadas());
	}

	static String envelope() {
		return "ST_MakeEnvelope(:oeste, :sul, :leste, :norte, 4674)";
	}

	private static ImovelNoMapa mapear(ResultSet linha, int n) throws SQLException {
		return new ImovelNoMapa(linha.getString("codigo_origem"), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), linha.getString("caminho"),
				SituacaoNoMapa.doBanco(linha.getString("situacao")), Precisao.valueOf(linha.getString("precisao")),
				linha.getDouble("longitude"), linha.getDouble("latitude"),
				linha.getTimestamp("atualizado_em").toInstant());
	}

}
