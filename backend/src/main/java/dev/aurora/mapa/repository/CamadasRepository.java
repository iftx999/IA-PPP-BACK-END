package dev.aurora.mapa.repository;

import java.sql.Date;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.CamadaSetor;
import dev.aurora.mapa.model.UnidadeOperacional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Camadas leves: malha dos municípios (dado público do IBGE, sem RLS), setores e
 * estações. A tabela {@code unidade} só tem RLS por tenant: o escopo é filtrado aqui
 * ({@code app_escopos()}).
 */
@Repository
public class CamadasRepository {

	/** Malha de um município do escopo (pela unidade). */
	public record Malha(UUID unidadeId, String nome, String codigoIbge, JsonNode geometria) {
	}

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CamadasRepository(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Competências com situação de imóveis no escopo, da mais recente à mais antiga. */
	public List<YearMonth> listarCompetencias() {
		return jdbc.sql("SELECT DISTINCT competencia FROM imovel_situacao_mensal ORDER BY competencia DESC LIMIT 36")
			.query((linha, n) -> YearMonth.from(linha.getDate(1).toLocalDate()))
			.list();
	}

	public List<Malha> listarMunicipios() {
		return jdbc.sql("""
				SELECT u.id, u.nome, u.codigo_ibge, g.geojson::text AS geojson
				  FROM unidade u
				  JOIN municipio_geometria g ON g.codigo_ibge = u.codigo_ibge
				 WHERE u.tipo = 'MUNICIPIO' AND u.ativo AND u.caminho <@ ANY (app_escopos())
				 ORDER BY u.nome""")
			.query((linha, n) -> new Malha(linha.getObject("id", UUID.class), linha.getString("nome"),
					linha.getString("codigo_ibge"), json.readTree(linha.getString("geojson"))))
			.list();
	}

	public List<CamadaSetor> listarSetores() {
		return jdbc.sql("""
				SELECT u.id, u.nome, g.origem, ST_AsGeoJSON(g.geom, 6) AS geojson
				  FROM setor_geometria g
				  JOIN unidade u ON u.id = g.unidade_id
				 WHERE u.ativo
				 ORDER BY u.nome""")
			.query((linha, n) -> new CamadaSetor(linha.getObject("id", UUID.class), linha.getString("nome"),
					linha.getString("origem"), json.readTree(linha.getString("geojson"))))
			.list();
	}

	public List<UnidadeOperacional> listarOperacionais() {
		return jdbc.sql("""
				SELECT id, tipo, nome, longitude, latitude FROM unidade
				 WHERE tipo IN ('ETE', 'EEE') AND ativo AND latitude IS NOT NULL AND longitude IS NOT NULL
				   AND caminho <@ ANY (app_escopos())
				 ORDER BY nome""")
			.query((linha, n) -> new UnidadeOperacional(linha.getObject("id", UUID.class), linha.getString("tipo"),
					linha.getString("nome"), linha.getDouble("longitude"), linha.getDouble("latitude")))
			.list();
	}

	/**
	 * Retângulo que envolve os municípios informados e os imóveis localizados do escopo.
	 */
	public Optional<Area> enquadrar(Collection<String> codigosIbge) {
		return jdbc.sql("""
				WITH partes AS (
				    SELECT ST_Extent(ST_GeomFromGeoJSON(geojson::text)) AS caixa
				      FROM municipio_geometria WHERE codigo_ibge IN (:codigos)
				    UNION ALL
				    SELECT ST_Extent(geom) FROM imovel_localizacao)
				SELECT ST_XMin(c) AS oeste, ST_YMin(c) AS sul, ST_XMax(c) AS leste, ST_YMax(c) AS norte
				  FROM (SELECT ST_Extent(caixa::geometry) AS c FROM partes WHERE caixa IS NOT NULL) x
				 WHERE c IS NOT NULL""")
			.param("codigos", codigosIbge.isEmpty() ? List.of("") : List.copyOf(codigosIbge))
			.query((linha, n) -> new Area(linha.getDouble("oeste"), linha.getDouble("sul"), linha.getDouble("leste"),
					linha.getDouble("norte")))
			.optional();
	}

	static Date inicio(YearMonth competencia) {
		return Date.valueOf(competencia.atDay(1));
	}

}
