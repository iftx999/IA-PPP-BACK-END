package dev.aurora.adesao.repository;

import java.sql.Date;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Gravação das medições de adesão a partir de {@code imovel_situacao_mensal}. */
@Repository
public class MedicaoRepository {

	/** Versão da regra de cálculo gravada em cada medição (RN-01). */
	public static final String VERSAO_REGRA = "ADESAO_V1";

	private final JdbcClient jdbc;

	MedicaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Uma instrução: soma por subárvore (caminho ltree) e grava ou atualiza a medição de
	 * cada unidade com imóveis com rede. POTENCIAL (sem rede) fica fora do denominador.
	 */
	public int recalcular(UUID indicador, YearMonth competencia) {
		return jdbc.sql("""
				INSERT INTO medicao (tenant_id, indicador_id, unidade_id, periodo_inicio, valor, numerador, denominador,
				                     versao_regra, calculado_em)
				SELECT app_tenant(), :indicador, u.id, :competencia, c.ligados * 100.0 / c.com_rede, c.ligados,
				       c.com_rede, :versao, now()
				  FROM unidade u
				  JOIN LATERAL (
				       SELECT count(*) FILTER (WHERE s.situacao = 'LIGADO') AS ligados,
				              count(*) FILTER (WHERE s.situacao IN ('LIGADO', 'FACTIVEL', 'CORTADO')) AS com_rede
				         FROM imovel_situacao_mensal s
				         JOIN unidade d ON d.id = s.unidade_id
				        WHERE s.competencia = :competencia AND d.caminho <@ u.caminho) c ON true
				 WHERE u.ativo AND u.tipo NOT IN ('ETE', 'EEE') AND c.com_rede > 0
				ON CONFLICT (tenant_id, indicador_id, unidade_id, periodo_inicio) DO UPDATE
				   SET valor = EXCLUDED.valor, numerador = EXCLUDED.numerador, denominador = EXCLUDED.denominador,
				       versao_regra = EXCLUDED.versao_regra, calculado_em = now()""")
			.param("indicador", indicador)
			.param("competencia", Date.valueOf(competencia.atDay(1)))
			.param("versao", VERSAO_REGRA)
			.update();
	}

	/** Tenant novo ainda sem o indicador de adesão: cria o padrão (RN-01). */
	public UUID garantirIndicador(String codigo) {
		jdbc.sql("""
				INSERT INTO indicador (id, tenant_id, codigo, nome, unidade_medida, sentido, periodicidade, origem)
				VALUES (gen_random_uuid(), app_tenant(), ?, 'Adesão à rede de esgoto', '%', 'MAIOR_MELHOR', 'MENSAL',
				        'CONTRATUAL')
				ON CONFLICT DO NOTHING""").param(codigo).update();
		return jdbc.sql("SELECT id FROM indicador WHERE codigo = ?").param(codigo).query(UUID.class).single();
	}

}
