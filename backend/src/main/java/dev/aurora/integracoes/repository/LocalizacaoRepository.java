package dev.aurora.integracoes.repository;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Localização dos imóveis ({@code imovel_localizacao}, RLS por escopo). */
@Repository
public class LocalizacaoRepository {

	public record Contagem(long novos, long atualizados, long preservados) {
	}

	private final JdbcClient jdbc;

	LocalizacaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Grava em lote numa instrução. O geocódigo não substitui ponto corrigido em campo ou
	 * desenhado (origem CAMPO/DESENHADA): esses contam como preservados.
	 */
	public Contagem gravar(List<String> matriculas, List<UUID> unidades, List<Double> longitudes,
			List<Double> latitudes, List<String> precisoes, @Nullable UUID usuario) {
		return jdbc
			.sql("""
					WITH dados AS (
					     SELECT * FROM unnest(CAST(:matriculas AS text[]), CAST(:unidades AS uuid[]),
					                          CAST(:longitudes AS float8[]), CAST(:latitudes AS float8[]),
					                          CAST(:precisoes AS text[])) AS d(codigo, unidade, lon, lat, precisao)),
					     gravados AS (
					     INSERT INTO imovel_localizacao (tenant_id, codigo_origem, unidade_id, geom, origem, precisao,
					                                     atualizado_por)
					     SELECT app_tenant(), codigo, unidade, ST_SetSRID(ST_MakePoint(lon, lat), 4674), 'GEOCODIFICADA',
					            precisao, :usuario
					       FROM dados
					     ON CONFLICT (tenant_id, codigo_origem) DO UPDATE
					        SET unidade_id = EXCLUDED.unidade_id, geom = EXCLUDED.geom, origem = EXCLUDED.origem,
					            precisao = EXCLUDED.precisao, atualizado_em = now(), atualizado_por = EXCLUDED.atualizado_por
					      WHERE imovel_localizacao.origem IN ('GEOCODIFICADA', 'IMPORTADA')
					        AND ((imovel_localizacao.unidade_id, imovel_localizacao.precisao, imovel_localizacao.origem)
					             IS DISTINCT FROM (EXCLUDED.unidade_id, EXCLUDED.precisao, EXCLUDED.origem)
					             OR NOT ST_Equals(imovel_localizacao.geom, EXCLUDED.geom))
					     RETURNING (xmax = 0) AS novo)
					SELECT count(*) FILTER (WHERE novo) AS novos, count(*) FILTER (WHERE NOT novo) AS atualizados,
					       (SELECT count(*) FROM dados d JOIN imovel_localizacao l ON l.codigo_origem = d.codigo
					         WHERE l.tenant_id = app_tenant() AND l.origem IN ('CAMPO', 'DESENHADA')) AS preservados
					  FROM gravados""")
			.param("matriculas", matriculas.toArray(String[]::new))
			.param("unidades", unidades.toArray(UUID[]::new))
			.param("longitudes", longitudes.toArray(Double[]::new))
			.param("latitudes", latitudes.toArray(Double[]::new))
			.param("precisoes", precisoes.toArray(String[]::new))
			.param("usuario", usuario)
			.query(Contagem.class)
			.single();
	}

}
