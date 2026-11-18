package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.Dispositivo;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Celulares do app de campo (J13; ADR 0045). */
@Repository
public class DispositivoRepository {

	private static final String SELECAO = """
			SELECT d.id, d.usuario_id, u.nome AS usuario_nome, d.modelo, d.sistema, d.versao_app, d.cadastrado_em,
			       d.ultimo_contato, d.revogado_em, r.nome AS revogado_por, d.apagado_em
			  FROM dispositivo d
			  JOIN usuario u ON u.id = d.usuario_id
			  LEFT JOIN usuario r ON r.id = d.revogado_por
			""";

	private final JdbcClient jdbc;

	DispositivoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Dispositivo> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE d.id = :id").param("id", id).query(DispositivoRepository::mapear).optional();
	}

	/** Revogados por último; os demais pelo último contato. */
	public List<Dispositivo> listar() {
		return jdbc.sql(SELECAO + " ORDER BY d.revogado_em IS NOT NULL, d.ultimo_contato DESC LIMIT 1000")
			.query(DispositivoRepository::mapear)
			.list();
	}

	/**
	 * Cadastra o celular ou, se já existe e não foi revogado, atualiza o usuário (celular
	 * corporativo usado em turnos), o modelo e a versão do app.
	 */
	public void cadastrar(UUID id, UUID usuario, String modelo, String sistema, String versaoApp) {
		jdbc.sql("""
				INSERT INTO dispositivo (id, tenant_id, usuario_id, modelo, sistema, versao_app)
				VALUES (:id, app_tenant(), :usuario, :modelo, :sistema, :versaoApp)
				ON CONFLICT (id) DO UPDATE
				   SET usuario_id = EXCLUDED.usuario_id, modelo = EXCLUDED.modelo, sistema = EXCLUDED.sistema,
				       versao_app = EXCLUDED.versao_app, ultimo_contato = now()
				 WHERE dispositivo.revogado_em IS NULL""")
			.param("id", id)
			.param("usuario", usuario)
			.param("modelo", modelo)
			.param("sistema", sistema)
			.param("versaoApp", versaoApp)
			.update();
	}

	/** Último contato, no máximo uma escrita por minuto. */
	public void tocar(UUID id) {
		jdbc.sql("""
				UPDATE dispositivo SET ultimo_contato = now()
				 WHERE id = :id AND ultimo_contato < now() - interval '1 minute'""").param("id", id).update();
	}

	/**
	 * @return false se já estava revogado
	 */
	public boolean revogar(UUID id, UUID por) {
		return jdbc.sql("""
				UPDATE dispositivo SET revogado_em = now(), revogado_por = :por
				 WHERE id = :id AND revogado_em IS NULL""").param("id", id).param("por", por).update() == 1;
	}

	public void apagado(UUID id) {
		jdbc.sql(
				"UPDATE dispositivo SET apagado_em = COALESCE(apagado_em, now()) WHERE id = :id AND revogado_em IS NOT NULL")
			.param("id", id)
			.update();
	}

	private static @Nullable Instant instante(ResultSet l, String coluna) throws SQLException {
		Timestamp t = l.getTimestamp(coluna);
		return t == null ? null : t.toInstant();
	}

	private static Dispositivo mapear(ResultSet l, int n) throws SQLException {
		return new Dispositivo(l.getObject("id", UUID.class), l.getObject("usuario_id", UUID.class),
				l.getString("usuario_nome"), l.getString("modelo"), l.getString("sistema"), l.getString("versao_app"),
				l.getTimestamp("cadastrado_em").toInstant(), l.getTimestamp("ultimo_contato").toInstant(),
				instante(l, "revogado_em"), l.getString("revogado_por"), instante(l, "apagado_em"));
	}

}
