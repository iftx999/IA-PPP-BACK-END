package dev.aurora.integracoes.repository;

import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Execuções de carga e gravação em lote da situação mensal dos imóveis. */
@Repository
public class CargaRepository {

	private final JdbcClient jdbc;

	CargaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void iniciar(UUID execucao, UUID fonte, YearMonth competencia, String origem, @Nullable UUID usuario,
			long lidos) {
		jdbc.sql("""
				INSERT INTO execucao_sincronizacao (id, tenant_id, fonte_dados_id, inicio, status, competencia, origem,
				                                    usuario_id, registros_lidos)
				VALUES (?, app_tenant(), ?, now(), 'EM_ANDAMENTO', ?, ?, ?, ?)""")
			.params(execucao, fonte, Date.valueOf(competencia.atDay(1)), origem, usuario, lidos)
			.update();
	}

	public record Contagem(long novos, long atualizados) {
	}

	/**
	 * Uma instrução para o lote inteiro (arrays + unnest). Linha igual à gravada não é
	 * reescrita; {@code xmax = 0} distingue inserção de atualização.
	 */
	public Contagem gravar(UUID fonte, UUID execucao, YearMonth competencia, List<String> matriculas,
			List<UUID> unidades, List<String> situacoes, List<@Nullable String> motivosCorte) {
		return jdbc.sql("""
				WITH dados AS (
				     SELECT * FROM unnest(CAST(:matriculas AS text[]), CAST(:unidades AS uuid[]),
				                          CAST(:situacoes AS text[]), CAST(:motivos AS text[]))
				              AS d(codigo, unidade, situacao, motivo)),
				     gravados AS (
				     INSERT INTO imovel_situacao_mensal (tenant_id, competencia, codigo_origem, unidade_id, situacao,
				                                         motivo_corte, fonte_dados_id, execucao_id)
				     SELECT app_tenant(), :competencia, codigo, unidade, situacao, motivo, :fonte, :execucao FROM dados
				     ON CONFLICT (tenant_id, competencia, codigo_origem) DO UPDATE
				        SET unidade_id = EXCLUDED.unidade_id, situacao = EXCLUDED.situacao,
				            motivo_corte = EXCLUDED.motivo_corte,
				            fonte_dados_id = EXCLUDED.fonte_dados_id, execucao_id = EXCLUDED.execucao_id,
				            atualizado_em = now()
				      WHERE (imovel_situacao_mensal.unidade_id, imovel_situacao_mensal.situacao,
				             imovel_situacao_mensal.motivo_corte)
				            IS DISTINCT FROM (EXCLUDED.unidade_id, EXCLUDED.situacao, EXCLUDED.motivo_corte)
				     RETURNING (xmax = 0) AS novo)
				SELECT count(*) FILTER (WHERE novo) AS novos, count(*) FILTER (WHERE NOT novo) AS atualizados
				  FROM gravados""")
			.param("matriculas", matriculas.toArray(String[]::new))
			.param("unidades", unidades.toArray(UUID[]::new))
			.param("situacoes", situacoes.toArray(String[]::new))
			.param("motivos", motivosCorte.toArray(String[]::new))
			.param("competencia", Date.valueOf(competencia.atDay(1)))
			.param("fonte", fonte)
			.param("execucao", execucao)
			.query(Contagem.class)
			.single();
	}

	public void concluir(UUID execucao, long gravados, long novos, long atualizados, long inalterados, long rejeitados,
			String rejeicoesJson, long comAviso, String avisosJson) {
		jdbc.sql("""
				UPDATE execucao_sincronizacao
				   SET status = 'SUCESSO', fim = now(), registros_gravados = ?, registros_novos = ?,
				       registros_atualizados = ?, registros_inalterados = ?, registros_rejeitados = ?,
				       rejeicoes = ?::jsonb, registros_com_aviso = ?, avisos = ?::jsonb
				 WHERE id = ?""")
			.params(gravados, novos, atualizados, inalterados, rejeitados, rejeicoesJson, comAviso, avisosJson,
					execucao)
			.update();
	}

	/** Matrículas desta carga que já tiveram conexão física, com um exemplo de cada. */
	public record JaConectados(long total, List<String> exemplos) {
	}

	/**
	 * RN-02: factível nunca teve conexão física. Entre as matrículas informadas como
	 * factíveis, as que aparecem ligadas ou cortadas numa competência anterior (usa o
	 * índice por matrícula).
	 */
	public JaConectados jaTiveramConexao(YearMonth competencia, List<String> factiveis, int exemplos) {
		if (factiveis.isEmpty()) {
			return new JaConectados(0, List.of());
		}
		var encontrados = jdbc.sql("""
				SELECT d.codigo FROM unnest(CAST(:matriculas AS text[])) AS d(codigo)
				 WHERE EXISTS (SELECT 1 FROM imovel_situacao_mensal s
				                WHERE s.tenant_id = app_tenant() AND s.codigo_origem = d.codigo
				                  AND s.competencia < :competencia AND s.situacao IN ('LIGADO', 'CORTADO'))
				 ORDER BY d.codigo""")
			.param("matriculas", factiveis.toArray(String[]::new))
			.param("competencia", Date.valueOf(competencia.atDay(1)))
			.query(String.class)
			.list();
		return new JaConectados(encontrados.size(),
				List.copyOf(encontrados.subList(0, Math.min(exemplos, encontrados.size()))));
	}

	public void falhar(UUID execucao, String erro, long rejeitados, String rejeicoesJson) {
		jdbc.sql("""
				UPDATE execucao_sincronizacao
				   SET status = 'FALHA', fim = now(), erro = ?, registros_rejeitados = ?, rejeicoes = ?::jsonb
				 WHERE id = ?""").params(erro, rejeitados, rejeicoesJson, execucao).update();
	}

}
