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
				WITH por_unidade AS (
				     -- Uma passada pelos imóveis da competência: contagem por unidade de lotação.
				     SELECT s.unidade_id,
				            count(*) FILTER (WHERE s.situacao = 'LIGADO') AS ligados,
				            count(*) FILTER (WHERE s.situacao IN ('LIGADO', 'FACTIVEL', 'CORTADO')) AS com_rede
				       FROM imovel_situacao_mensal s
				      WHERE s.competencia = :competencia
				      GROUP BY s.unidade_id),
				     agregado AS (
				     -- Cada unidade soma as contagens da própria subárvore (RN-01: soma, não média).
				     SELECT u.id, sum(p.ligados)::bigint AS ligados, sum(p.com_rede)::bigint AS com_rede
				       FROM unidade u
				       JOIN unidade d ON d.caminho <@ u.caminho
				       JOIN por_unidade p ON p.unidade_id = d.id
				      WHERE u.ativo AND u.tipo NOT IN ('ETE', 'EEE')
				      GROUP BY u.id)
				INSERT INTO medicao (tenant_id, indicador_id, unidade_id, periodo_inicio, valor, numerador, denominador,
				                     versao_regra, calculado_em)
				SELECT app_tenant(), :indicador, a.id, :competencia, a.ligados * 100.0 / a.com_rede, a.ligados,
				       a.com_rede, :versao, now()
				  FROM agregado a
				 WHERE a.com_rede > 0
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
