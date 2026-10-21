package dev.aurora.acesso.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.acesso.model.Concessao;
import dev.aurora.acesso.model.UsuarioAutenticado;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de usuário; todas rodam dentro do contexto de tenant (RLS). */
@Repository
public class UsuarioRepository {

	private final JdbcClient jdbc;

	UsuarioRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<UUID> buscarAtivoPorSub(String sub) {
		return jdbc.sql("SELECT id FROM usuario WHERE sub_externo = ? AND ativo")
			.param(sub)
			.query(UUID.class)
			.optional();
	}

	/** Vincula o sub ao usuário ativo, ainda sem vínculo, que tem esse e-mail. */
	public Optional<UUID> vincularSubPorEmail(String sub, String email) {
		return jdbc.sql("""
				UPDATE usuario SET sub_externo = ?
				 WHERE lower(email) = lower(?) AND sub_externo IS NULL AND ativo
				RETURNING id""")
			.param(sub)
			.param(email)
			.query(UUID.class)
			.optional();
	}

	/** Concessões vigentes, em unidades ativas. */
	public List<Concessao> listarConcessoesVigentes(UUID usuarioId) {
		return jdbc.sql("""
				SELECT pp.permissao, u.caminho::text AS caminho_unidade
				  FROM usuario_perfil_escopo upe
				  JOIN perfil_permissao pp ON pp.perfil_id = upe.perfil_id
				  JOIN unidade u ON u.id = upe.unidade_id AND u.ativo
				 WHERE upe.usuario_id = ?
				   AND (upe.valido_ate IS NULL OR upe.valido_ate > now())""")
			.param(usuarioId)
			.query(Concessao.class)
			.list();
	}

	/** Atualiza o último acesso no máximo a cada 5 minutos, para não gravar em toda requisição. */
	public void registrarAcesso(UUID usuarioId) {
		jdbc.sql("""
				UPDATE usuario SET ultimo_acesso = now()
				 WHERE id = ? AND (ultimo_acesso IS NULL OR ultimo_acesso < now() - interval '5 minutes')""")
			.param(usuarioId)
			.update();
	}

	public Optional<UsuarioAutenticado> buscarAutenticado(UUID usuarioId, List<String> permissoes) {
		return jdbc.sql("""
				SELECT u.id, u.nome, u.email, t.id AS tenant_id, t.nome AS tenant
				  FROM usuario u JOIN tenant t ON t.id = u.tenant_id
				 WHERE u.id = ?""")
			.param(usuarioId)
			.query((linha, n) -> new UsuarioAutenticado(linha.getObject("id", UUID.class), linha.getString("nome"),
					linha.getString("email"), linha.getObject("tenant_id", UUID.class), linha.getString("tenant"),
					permissoes))
			.optional();
	}

}
