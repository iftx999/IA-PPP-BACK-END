package dev.aurora.mapa.repository;

import dev.aurora.mapa.model.FiltroImoveis;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tiles vetoriais (Mapbox Vector Tile) gerados pelo PostGIS ({@code ST_AsMVT}). Até o
 * zoom {@value #ZOOM_IMOVEIS} os imóveis vêm agrupados numa grade (camada
 * {@code agrupamentos}); a partir dele, um ponto por imóvel (camada {@code imoveis}), sem
 * matrícula: o detalhe é pedido ao clicar. A rede aparece a partir do zoom
 * {@value #ZOOM_REDE}.
 */
@Repository
public class TileRepository {

	public static final int ZOOM_IMOVEIS = 15;

	public static final int ZOOM_REDE = 12;

	/** Células da grade de agrupamento por lado do tile. */
	private static final int CELULAS = 16;

	private static final String LIMITES = """
			WITH limites AS (
			    SELECT ST_TileEnvelope(:z, :x, :y) AS env3857,
			           ST_Transform(ST_TileEnvelope(:z, :x, :y, margin => 0.015625), 4674) AS env),
			pontos AS (
			    SELECT ST_Transform(l.geom, 3857) AS p, s.situacao, l.precisao <> 'ENDERECO' AS aproximada
			      FROM imovel_localizacao l
			      JOIN imovel_situacao_mensal s ON s.tenant_id = l.tenant_id AND s.codigo_origem = l.codigo_origem
			                                   AND s.competencia = :competencia
			     WHERE l.geom && (SELECT env FROM limites)
			       AND s.situacao = ANY (:situacoes)
			       AND (:aproximadas OR l.precisao = 'ENDERECO'))
			""";

	private static final String IMOVEIS = """
			SELECT ST_AsMVT(t, 'imoveis', 4096, 'geom') FROM (
			    SELECT ST_AsMVTGeom(p, (SELECT env3857 FROM limites), 4096, 64, true) AS geom,
			           CASE situacao WHEN 'POTENCIAL' THEN 'SEM_REDE' ELSE situacao END AS situacao, aproximada
			      FROM pontos) t
			""";

	/**
	 * Grade em coordenadas do tile: o centro de cada célula é a média dos pontos dela,
	 * com a contagem por situação para o rótulo.
	 */
	private static final String AGRUPAMENTOS = """
			SELECT ST_AsMVT(t, 'agrupamentos', 4096, 'geom') FROM (
			    SELECT ST_AsMVTGeom(ST_Centroid(ST_Collect(p)), (SELECT env3857 FROM limites), 4096, 64, true) AS geom,
			           count(*) AS quantidade,
			           count(*) FILTER (WHERE situacao = 'LIGADO') AS ligados,
			           count(*) FILTER (WHERE situacao IN ('FACTIVEL', 'CORTADO')) AS sem_ligacao
			      FROM pontos
			     GROUP BY ST_SnapToGrid(p, ST_XMin((SELECT env3857 FROM limites)),
			                               ST_YMin((SELECT env3857 FROM limites)),
			                               (ST_XMax((SELECT env3857 FROM limites)) - ST_XMin((SELECT env3857 FROM limites))) / %d,
			                               (ST_YMax((SELECT env3857 FROM limites)) - ST_YMin((SELECT env3857 FROM limites))) / %d)) t
			"""
		.formatted(CELULAS, CELULAS);

	private static final String REDE = """
			SELECT ST_AsMVT(t, 'rede', 4096, 'geom') FROM (
			    SELECT ST_AsMVTGeom(ST_Transform(r.geom, 3857), (SELECT env3857 FROM limites), 4096, 64, true) AS geom,
			           r.situacao, r.diametro_mm, r.material
			      FROM rede_esgoto r
			     WHERE r.geom && (SELECT env FROM limites)) t
			""";

	private final JdbcClient jdbc;

	TileRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Tile z/x/y com as camadas do zoom; vazio quando nada cai nele.
	 * @param rede se a camada de rede está ligada
	 */
	public byte[] gerar(int z, int x, int y, FiltroImoveis filtro, boolean rede) {
		var camadas = new StringBuilder(LIMITES).append("SELECT (")
			.append(z >= ZOOM_IMOVEIS ? IMOVEIS : AGRUPAMENTOS)
			.append(")");
		if (rede && z >= ZOOM_REDE) {
			camadas.append(" || (").append(REDE).append(")");
		}
		var tile = jdbc.sql(camadas.toString())
			.param("z", z)
			.param("x", x)
			.param("y", y)
			.param("competencia", CamadasRepository.inicio(filtro.competencia()))
			.param("situacoes", filtro.situacoesNoBanco())
			.param("aproximadas", filtro.aproximadas())
			.query(byte[].class)
			.optional()
			.orElse(null);
		return tile == null ? new byte[0] : tile;
	}

}
