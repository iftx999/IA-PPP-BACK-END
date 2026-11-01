package dev.aurora.acesso.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Eventos de acesso já copiados para a trilha (ADR 0028), no tenant do contexto (RLS).
 * Serve só para não importar o mesmo evento duas vezes.
 */
@Repository
public class EventoAcessoImportadoRepository {

	private final JdbcClient jdbc;

	EventoAcessoImportadoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Momento do evento mais recente já importado. */
	public Optional<Instant> maisRecente() {
		return jdbc.sql("SELECT ocorrido_em FROM evento_acesso_importado ORDER BY ocorrido_em DESC LIMIT 1")
			.query(OffsetDateTime.class)
			.optional()
			.map(OffsetDateTime::toInstant);
	}

	/**
	 * @return false se o evento já tinha sido importado
	 */
	public boolean marcar(String idExterno, Instant ocorridoEm) {
		return jdbc.sql("""
				INSERT INTO evento_acesso_importado (tenant_id, id_externo, ocorrido_em)
				VALUES (app_tenant(), ?, ?) ON CONFLICT DO NOTHING""")
			.params(idExterno, ocorridoEm.atOffset(ZoneOffset.UTC))
			.update() == 1;
	}

	/** Esquece eventos que o provedor já não devolve (ele guarda 30 dias). */
	public int apagarAntesDe(Instant limite) {
		return jdbc.sql("DELETE FROM evento_acesso_importado WHERE ocorrido_em < ?")
			.param(limite.atOffset(ZoneOffset.UTC))
			.update();
	}

}
