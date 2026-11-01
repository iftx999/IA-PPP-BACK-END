package dev.aurora.auditoria.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.aurora.auditoria.model.Expurgo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Prazo de retenção da trilha e expurgos do tenant atual (ADR 0029). Apagar eventos só é
 * possível pela função {@code expurgar_auditoria_vencida()}, que respeita o prazo.
 */
@Repository
public class RetencaoRepository {

	private final JdbcClient jdbc;

	RetencaoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public int meses() {
		return jdbc.sql("SELECT auditoria_retencao_meses FROM tenant WHERE id = app_tenant()")
			.query(Integer.class)
			.single();
	}

	public void alterarMeses(int meses) {
		jdbc.sql("UPDATE tenant SET auditoria_retencao_meses = ? WHERE id = app_tenant()").param(meses).update();
	}

	/** Último expurgo: a âncora da cadeia que ficou. */
	public Optional<Expurgo> ultimoExpurgo() {
		return jdbc.sql("""
				SELECT executado_em, limite, ate_sequencia, quantidade, ultimo_hash FROM auditoria_expurgo
				 ORDER BY ate_sequencia DESC LIMIT 1""")
			.query((r, n) -> new Expurgo(r.getObject("executado_em", OffsetDateTime.class).toInstant(),
					r.getObject("limite", OffsetDateTime.class).toInstant(), r.getLong("ate_sequencia"),
					r.getLong("quantidade"), r.getBytes("ultimo_hash")))
			.optional();
	}

	/** Total de eventos já expurgados no tenant. */
	public long totalExpurgado() {
		return jdbc.sql("SELECT coalesce(sum(quantidade), 0) FROM auditoria_expurgo").query(Long.class).single();
	}

	/**
	 * @return quantos eventos vencidos foram apagados agora
	 */
	public long expurgarVencidos() {
		return jdbc.sql("SELECT expurgar_auditoria_vencida()").query(Long.class).single();
	}

	/**
	 * @return true se a partição do ano foi criada agora
	 */
	public boolean garantirParticao(int ano) {
		return Boolean.TRUE
			.equals(jdbc.sql("SELECT garantir_particao_auditoria(?)").param(ano).query(Boolean.class).single());
	}

}
