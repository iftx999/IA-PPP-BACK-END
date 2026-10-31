package dev.aurora.alertas.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.alertas.model.PreferenciaEmail;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Preferência de e-mail dos alertas por pessoa; sem linha vale o padrão. */
@Repository
public class PreferenciaRepository {

	private final JdbcClient jdbc;

	PreferenciaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<PreferenciaEmail> buscar(UUID usuario) {
		return jdbc.sql("SELECT email FROM alerta_preferencia WHERE usuario_id = ?")
			.param(usuario)
			.query((r, n) -> PreferenciaEmail.valueOf(r.getString(1)))
			.optional();
	}

	public void salvar(UUID usuario, PreferenciaEmail email) {
		jdbc.sql("""
				INSERT INTO alerta_preferencia (tenant_id, usuario_id, email) VALUES (app_tenant(), ?, ?)
				ON CONFLICT (tenant_id, usuario_id) DO UPDATE SET email = EXCLUDED.email, atualizado_em = now()""")
			.params(usuario, email.name())
			.update();
	}

	public Map<UUID, PreferenciaEmail> listar(Collection<UUID> usuarios) {
		var preferencias = new HashMap<UUID, PreferenciaEmail>();
		if (!usuarios.isEmpty()) {
			jdbc.sql("SELECT usuario_id, email FROM alerta_preferencia WHERE usuario_id IN (:usuarios)")
				.param("usuarios", List.copyOf(usuarios))
				.query(r -> {
					preferencias.put(r.getObject("usuario_id", UUID.class), PreferenciaEmail.valueOf(r.getString(2)));
				});
		}
		return preferencias;
	}

}
