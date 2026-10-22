package dev.aurora.alertas.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.alertas.model.Alerta;
import dev.aurora.alertas.model.Condicao;
import dev.aurora.alertas.model.EstadoAlerta;
import dev.aurora.alertas.model.Severidade;
import dev.aurora.alertas.model.TipoAlerta;
import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Alertas do tenant; o RLS mostra os sem unidade e os das unidades do escopo. */
@Repository
public class AlertaRepository {

	private static final String SELECAO = """
			SELECT a.id, a.tipo, a.severidade, a.unidade_id, u.nome AS unidade_nome, a.chave, a.titulo, a.descricao,
			       a.link, a.criado_em, a.atualizado_em, r.nome AS reconhecido_por, a.reconhecido_em, a.resolvido_em
			  FROM alerta a
			  LEFT JOIN unidade u ON u.id = a.unidade_id
			  LEFT JOIN usuario r ON r.id = a.reconhecido_por""";

	private final JdbcClient jdbc;

	AlertaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Alerta> abertosDosTipos(Collection<TipoAlerta> tipos) {
		return jdbc.sql(SELECAO + " WHERE a.resolvido_em IS NULL AND a.tipo = ANY (?) FOR UPDATE OF a")
			.param(tipos.stream().map(Enum::name).toArray(String[]::new))
			.query((r, n) -> alerta(r))
			.list();
	}

	/** Abre o alerta; se outra execução já abriu a mesma chave, não faz nada. */
	public boolean abrir(Condicao c) {
		return jdbc.sql("""
				INSERT INTO alerta (id, tenant_id, tipo, severidade, unidade_id, chave, titulo, descricao, link)
				VALUES (?, app_tenant(), ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (tenant_id, chave) WHERE resolvido_em IS NULL DO NOTHING""")
			.params(UUID.randomUUID(), c.tipo().name(), c.severidade().name(), c.unidadeId(), c.chave(), c.titulo(),
					c.descricao(), c.link())
			.update() == 1;
	}

	public void atualizar(UUID id, Condicao c) {
		jdbc.sql("""
				UPDATE alerta SET severidade = ?, titulo = ?, descricao = ?, link = ?, atualizado_em = now()
				 WHERE id = ?""").params(c.severidade().name(), c.titulo(), c.descricao(), c.link(), id).update();
	}

	public void resolver(Collection<UUID> ids) {
		if (!ids.isEmpty()) {
			jdbc.sql("UPDATE alerta SET resolvido_em = now(), atualizado_em = now() WHERE id = ANY (?)")
				.param(ids.toArray(UUID[]::new))
				.update();
		}
	}

	public List<Alerta> listar(EstadoAlerta estado, int limite) {
		var filtro = switch (estado) {
			case ABERTOS -> "a.resolvido_em IS NULL";
			case RECONHECIDOS -> "a.resolvido_em IS NULL AND a.reconhecido_em IS NOT NULL";
			case RESOLVIDOS -> "a.resolvido_em IS NOT NULL";
		};
		var ordem = estado == EstadoAlerta.RESOLVIDOS ? "a.resolvido_em DESC"
				: "a.severidade = 'CRITICO' DESC, a.reconhecido_em IS NOT NULL, a.criado_em DESC";
		return jdbc.sql(SELECAO + " WHERE " + filtro + " ORDER BY " + ordem + " LIMIT ?")
			.param(limite)
			.query((r, n) -> alerta(r))
			.list();
	}

	public Optional<Alerta> buscar(UUID id) {
		return jdbc.sql(SELECAO + " WHERE a.id = ?").param(id).query((r, n) -> alerta(r)).optional();
	}

	public boolean reconhecer(UUID id, @Nullable UUID usuario) {
		return jdbc.sql("""
				UPDATE alerta SET reconhecido_por = ?, reconhecido_em = now(), atualizado_em = now()
				 WHERE id = ? AND resolvido_em IS NULL AND reconhecido_em IS NULL""").params(usuario, id).update() == 1;
	}

	/** Abertos por severidade, sem os reconhecidos (são os que pedem ação). */
	public record Contagem(long criticos, long atencao, long reconhecidos) {
	}

	public Contagem contar() {
		return jdbc.sql("""
				SELECT count(*) FILTER (WHERE severidade = 'CRITICO' AND reconhecido_em IS NULL) AS criticos,
				       count(*) FILTER (WHERE severidade = 'ATENCAO' AND reconhecido_em IS NULL) AS atencao,
				       count(*) FILTER (WHERE reconhecido_em IS NOT NULL) AS reconhecidos
				  FROM alerta WHERE resolvido_em IS NULL""").query(Contagem.class).single();
	}

	private static @Nullable Instant instante(@Nullable Timestamp t) {
		return t == null ? null : t.toInstant();
	}

	private static Alerta alerta(ResultSet r) throws SQLException {
		return new Alerta(r.getObject("id", UUID.class), TipoAlerta.valueOf(r.getString("tipo")),
				Severidade.valueOf(r.getString("severidade")), r.getObject("unidade_id", UUID.class),
				r.getString("unidade_nome"), r.getString("chave"), r.getString("titulo"), r.getString("descricao"),
				r.getString("link"), r.getTimestamp("criado_em").toInstant(),
				r.getTimestamp("atualizado_em").toInstant(), r.getString("reconhecido_por"),
				instante(r.getTimestamp("reconhecido_em")), instante(r.getTimestamp("resolvido_em")));
	}

}
