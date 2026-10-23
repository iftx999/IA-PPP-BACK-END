package dev.aurora.mapa.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import dev.aurora.mapa.model.Area;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.NovaMarcacao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Marcações de visita ({@code marcacao_mapa}, RLS por escopo). */
@Repository
public class MarcacaoRepository {

	private static final String SELECAO = """
			SELECT m.id, m.unidade_id, u.nome AS unidade_nome, ST_X(m.geom) AS longitude, ST_Y(m.geom) AS latitude,
			       m.titulo, m.observacao, m.criada_por, coalesce(p.nome, p.email, 'Usuário removido') AS autor_nome,
			       m.criada_em
			  FROM marcacao_mapa m
			  JOIN unidade u ON u.id = m.unidade_id
			  LEFT JOIN usuario p ON p.id = m.criada_por
			""";

	private final JdbcClient jdbc;

	MarcacaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Unidade mais específica do escopo cujo polígono contém o ponto: setor (polígono
	 * próprio) ou município (malha do IBGE).
	 */
	public Optional<UUID> buscarUnidadeNoPonto(double longitude, double latitude) {
		return jdbc.sql("""
				WITH c AS (SELECT ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674) AS ponto)
				SELECT u.id
				  FROM unidade u
				  LEFT JOIN setor_geometria s ON s.tenant_id = u.tenant_id AND s.unidade_id = u.id
				  LEFT JOIN municipio_geometria g ON g.codigo_ibge = u.codigo_ibge
				 CROSS JOIN c
				 WHERE u.ativo AND u.caminho <@ ANY (app_escopos())
				   AND (ST_Intersects(s.geom, c.ponto)
				        OR ST_Intersects(ST_SetSRID(ST_GeomFromGeoJSON(g.geojson::text), 4674), c.ponto))
				 ORDER BY nlevel(u.caminho) DESC
				 LIMIT 1""").param("longitude", longitude).param("latitude", latitude).query(UUID.class).optional();
	}

	public void inserir(UUID id, UUID unidade, NovaMarcacao nova, UUID autor) {
		jdbc.sql("""
				INSERT INTO marcacao_mapa (id, tenant_id, unidade_id, geom, titulo, observacao, criada_por)
				VALUES (:id, app_tenant(), :unidade, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4674),
				        :titulo, :observacao, :autor)""")
			.param("id", id)
			.param("unidade", unidade)
			.param("longitude", nova.longitude())
			.param("latitude", nova.latitude())
			.param("titulo", nova.titulo())
			.param("observacao", nova.observacao())
			.param("autor", autor)
			.update();
	}

	public Optional<Marcacao> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE m.id = :id").param("id", id).query(MarcacaoRepository::mapear).optional();
	}

	/** Marcações da área, das mais novas às mais antigas. */
	public List<Marcacao> listar(Area area, int limite) {
		return jdbc
			.sql(SELECAO + " WHERE m.geom && " + ImovelMapaRepository.envelope()
					+ " ORDER BY m.criada_em DESC, m.id LIMIT :limite")
			.param("oeste", area.oeste())
			.param("sul", area.sul())
			.param("leste", area.leste())
			.param("norte", area.norte())
			.param("limite", limite)
			.query(MarcacaoRepository::mapear)
			.list();
	}

	public void percorrer(Area area, Consumer<Marcacao> consumidor) {
		try (var registros = jdbc
			.sql(SELECAO + " WHERE m.geom && " + ImovelMapaRepository.envelope() + " ORDER BY m.criada_em, m.id")
			.param("oeste", area.oeste())
			.param("sul", area.sul())
			.param("leste", area.leste())
			.param("norte", area.norte())
			.query(MarcacaoRepository::mapear)
			.stream()) {
			registros.forEach(consumidor);
		}
	}

	public boolean remover(UUID id) {
		return jdbc.sql("DELETE FROM marcacao_mapa WHERE id = ?").param(id).update() == 1;
	}

	private static Marcacao mapear(ResultSet linha, int n) throws SQLException {
		return new Marcacao(linha.getObject("id", UUID.class), linha.getObject("unidade_id", UUID.class),
				linha.getString("unidade_nome"), linha.getDouble("longitude"), linha.getDouble("latitude"),
				linha.getString("titulo"), linha.getString("observacao"), linha.getObject("criada_por", UUID.class),
				linha.getString("autor_nome"), linha.getTimestamp("criada_em").toInstant());
	}

}
