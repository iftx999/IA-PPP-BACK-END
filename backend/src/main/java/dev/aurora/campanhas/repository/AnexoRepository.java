package dev.aurora.campanhas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.campanhas.model.Anexo;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Fotos das OS enviadas pelo app em partes (10.9.3; ADR 0045). */
@Repository
public class AnexoRepository {

	private static final String SELECAO = """
			SELECT id, os_id, parametro, tipo_midia, tamanho, recebido, sha256, situacao, recusa, usuario_id, criado_em,
			       concluido_em
			  FROM os_anexo
			""";

	private final JdbcClient jdbc;

	AnexoRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Anexo> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE id = :id").param("id", id).query(AnexoRepository::mapear).optional();
	}

	public List<Anexo> daOrdem(UUID os) {
		return jdbc.sql(SELECAO + " WHERE os_id = :os ORDER BY criado_em, id")
			.param("os", os)
			.query(AnexoRepository::mapear)
			.list();
	}

	/**
	 * @return false se outro envio criou o mesmo anexo antes
	 */
	public boolean criar(UUID id, UUID os, UUID unidade, @Nullable String parametro, String tipoMidia, int tamanho,
			String sha256, UUID usuario, UUID dispositivo) {
		return jdbc.sql("""
				INSERT INTO os_anexo (id, tenant_id, os_id, unidade_id, parametro, tipo_midia, tamanho, sha256,
				                      usuario_id, dispositivo_id)
				VALUES (:id, app_tenant(), :os, :unidade, :parametro, :tipo, :tamanho, :sha256, :usuario, :dispositivo)
				ON CONFLICT DO NOTHING""")
			.param("id", id)
			.param("os", os)
			.param("unidade", unidade)
			.param("parametro", parametro)
			.param("tipo", tipoMidia)
			.param("tamanho", tamanho)
			.param("sha256", sha256)
			.param("usuario", usuario)
			.param("dispositivo", dispositivo)
			.update() == 1;
	}

	/**
	 * Acrescenta a parte que começa em {@code inicio} (deve ser exatamente o que já foi
	 * recebido).
	 * @return false se o recebido mudou (envio concorrente)
	 */
	public boolean acrescentar(UUID id, int inicio, byte[] parte) {
		return jdbc.sql("""
				UPDATE os_anexo SET conteudo = conteudo || :parte, recebido = recebido + :tamanho
				 WHERE id = :id AND recebido = :inicio AND situacao = 'RECEBENDO'""")
			.param("id", id)
			.param("parte", parte)
			.param("tamanho", parte.length)
			.param("inicio", inicio)
			.update() == 1;
	}

	public byte[] conteudo(UUID id) {
		return jdbc.sql("SELECT conteudo FROM os_anexo WHERE id = :id").param("id", id).query(byte[].class).single();
	}

	/** Disponível ou recusado; o conteúdo recusado é descartado. */
	public void concluir(UUID id, boolean disponivel, @Nullable String recusa) {
		jdbc.sql("""
				UPDATE os_anexo
				   SET situacao = :situacao, recusa = :recusa, concluido_em = now(),
				       conteudo = CASE WHEN :disponivel THEN conteudo ELSE '\\x'::bytea END,
				       recebido = CASE WHEN :disponivel THEN recebido ELSE 0 END
				 WHERE id = :id""")
			.param("id", id)
			.param("situacao", disponivel ? "DISPONIVEL" : "RECUSADO")
			.param("recusa", recusa)
			.param("disponivel", disponivel)
			.update();
	}

	private static Anexo mapear(ResultSet l, int n) throws SQLException {
		var concluido = l.getTimestamp("concluido_em");
		return new Anexo(l.getObject("id", UUID.class), l.getObject("os_id", UUID.class), l.getString("parametro"),
				l.getString("tipo_midia"), l.getInt("tamanho"), l.getInt("recebido"), l.getString("sha256"),
				l.getString("situacao"), l.getString("recusa"), l.getObject("usuario_id", UUID.class),
				l.getTimestamp("criado_em").toInstant(), concluido == null ? null : concluido.toInstant());
	}

}
