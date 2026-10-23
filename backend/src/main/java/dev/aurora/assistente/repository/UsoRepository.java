package dev.aurora.assistente.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.Optional;

import dev.aurora.assistente.model.Uso;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Uso e custo do assistente por cliente e mês, e o limite contratado (se houver). */
@Repository
public class UsoRepository {

	public record Limite(Long tokensPorMes, BigDecimal usdPorMes) {
	}

	public record UsoDoMes(long perguntas, long tokens, BigDecimal custoUsd) {
	}

	private final JdbcClient jdbc;

	UsoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Limite> buscarLimite() {
		return jdbc.sql("SELECT tokens_por_mes, usd_por_mes FROM assistente_limite")
			.query((linha, n) -> new Limite((Long) linha.getObject("tokens_por_mes"),
					linha.getBigDecimal("usd_por_mes")))
			.optional();
	}

	public UsoDoMes buscarUso(YearMonth mes) {
		return jdbc
			.sql("""
					SELECT coalesce(sum(perguntas), 0) AS perguntas,
					       coalesce(sum(tokens_entrada + tokens_saida + tokens_cache_lido + tokens_cache_escrito), 0) AS tokens,
					       coalesce(sum(custo_usd), 0) AS custo_usd
					  FROM assistente_uso_mensal WHERE mes = ?""")
			.param(Date.valueOf(mes.atDay(1)))
			.query(UsoDoMes.class)
			.single();
	}

	public void somar(YearMonth mes, Uso uso, BigDecimal custo, boolean semCusto) {
		jdbc.sql("""
				INSERT INTO assistente_uso_mensal AS u (tenant_id, mes, perguntas, tokens_entrada, tokens_saida,
				                                       tokens_cache_lido, tokens_cache_escrito, custo_usd,
				                                       perguntas_sem_custo)
				VALUES (app_tenant(), :mes, 1, :entrada, :saida, :lido, :escrito, :custo, :sem_custo)
				ON CONFLICT (tenant_id, mes) DO UPDATE
				   SET perguntas = u.perguntas + 1,
				       tokens_entrada = u.tokens_entrada + EXCLUDED.tokens_entrada,
				       tokens_saida = u.tokens_saida + EXCLUDED.tokens_saida,
				       tokens_cache_lido = u.tokens_cache_lido + EXCLUDED.tokens_cache_lido,
				       tokens_cache_escrito = u.tokens_cache_escrito + EXCLUDED.tokens_cache_escrito,
				       custo_usd = u.custo_usd + EXCLUDED.custo_usd,
				       perguntas_sem_custo = u.perguntas_sem_custo + EXCLUDED.perguntas_sem_custo""")
			.param("mes", Date.valueOf(mes.atDay(1)))
			.param("entrada", uso.entrada())
			.param("saida", uso.saida())
			.param("lido", uso.cacheLido())
			.param("escrito", uso.cacheEscrito())
			.param("custo", custo)
			.param("sem_custo", semCusto ? 1 : 0)
			.update();
	}

}
