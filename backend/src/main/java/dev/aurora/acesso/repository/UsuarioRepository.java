package dev.aurora.acesso.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.aurora.acesso.model.Concessao;
import dev.aurora.acesso.model.ConcessaoDoUsuario;
import dev.aurora.acesso.model.NovaConcessao;
import dev.aurora.acesso.model.Permissao;
import dev.aurora.acesso.model.Usuario;
import dev.aurora.acesso.model.UsuarioAutenticado;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de usuário; todas rodam dentro do contexto de tenant (RLS). */
@Repository
public class UsuarioRepository {

	private static final String SELECAO_USUARIO = """
			SELECT id, nome, email, sub_externo, ativo, ultimo_acesso, convidado_em FROM usuario""";

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

	/**
	 * Vincula o sub ao usuário ativo com esse e-mail. Idempotente: requisições
	 * simultâneas do primeiro login (ex.: /eu e a tela) aceitam a linha já vinculada ao
	 * mesmo sub. Se outra requisição estiver vinculando, o UPDATE espera o lock e
	 * reavalia a condição sobre a versão gravada.
	 */
	public Optional<UUID> vincularSubPorEmail(String sub, String email) {
		return jdbc.sql("""
				UPDATE usuario SET sub_externo = :sub
				 WHERE lower(email) = lower(:email) AND ativo
				   AND (sub_externo IS NULL OR sub_externo = :sub)
				RETURNING id""").param("sub", sub).param("email", email).query(UUID.class).optional();
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

	/**
	 * Atualiza o último acesso no máximo a cada 5 minutos, para não gravar em toda
	 * requisição.
	 */
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

	/** Usuários do tenant, sem as concessões (ver {@link #listarConcessoes}). */
	public List<Usuario> listar() {
		return jdbc.sql(SELECAO_USUARIO + " ORDER BY lower(nome), lower(email)").query((r, n) -> usuario(r)).list();
	}

	public Optional<Usuario> buscar(UUID id) {
		return jdbc.sql(SELECAO_USUARIO + " WHERE id = ?").param(id).query((r, n) -> usuario(r)).optional();
	}

	/**
	 * Trava o usuário para alteração: duas operações simultâneas não furam a regra do
	 * último administrador.
	 */
	public Optional<Usuario> travar(UUID id) {
		return jdbc.sql(SELECAO_USUARIO + " WHERE id = ? FOR UPDATE").param(id).query((r, n) -> usuario(r)).optional();
	}

	public boolean existeEmail(String email) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = lower(?))")
			.param(email)
			.query(Boolean.class)
			.single();
	}

	public void inserirConvidado(UUID id, String subExterno, String email, String nome) {
		jdbc.sql("""
				INSERT INTO usuario (id, tenant_id, sub_externo, email, nome, convidado_em)
				VALUES (?, app_tenant(), ?, ?, ?, now())""").params(id, subExterno, email, nome).update();
	}

	/** Convidado antigo, ainda sem conta no provedor de identidade. */
	public void vincularSub(UUID id, String subExterno) {
		jdbc.sql("UPDATE usuario SET sub_externo = ? WHERE id = ?").params(subExterno, id).update();
	}

	public void marcarConvidado(UUID id) {
		jdbc.sql("UPDATE usuario SET convidado_em = now() WHERE id = ?").param(id).update();
	}

	public void definirAtivo(UUID id, boolean ativo) {
		jdbc.sql("UPDATE usuario SET ativo = ? WHERE id = ?").params(ativo, id).update();
	}

	/**
	 * Todas as concessões (vigentes ou não) dos usuários informados, com nomes de perfil
	 * e unidade, agrupadas por usuário.
	 */
	public Map<UUID, List<ConcessaoDoUsuario>> listarConcessoes(Collection<UUID> usuarios) {
		if (usuarios.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("""
				SELECT upe.id, upe.usuario_id, upe.perfil_id, p.nome AS perfil, upe.unidade_id, un.nome AS unidade,
				       upe.valido_ate
				  FROM usuario_perfil_escopo upe
				  JOIN perfil p ON p.id = upe.perfil_id
				  JOIN unidade un ON un.id = upe.unidade_id
				 WHERE upe.usuario_id = ANY (:usuarios)
				 ORDER BY un.caminho, p.nome""")
			.param("usuarios", usuarios.toArray(UUID[]::new))
			.query((r, n) -> new ConcessaoComUsuario(r.getObject("usuario_id", UUID.class),
					new ConcessaoDoUsuario(r.getObject("id", UUID.class), r.getObject("perfil_id", UUID.class),
							r.getString("perfil"), r.getObject("unidade_id", UUID.class), r.getString("unidade"),
							r.getObject("valido_ate", OffsetDateTime.class))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(ConcessaoComUsuario::usuario, LinkedHashMap::new,
					Collectors.mapping(ConcessaoComUsuario::concessao, Collectors.toList())));
	}

	public void substituirConcessoes(UUID usuario, Collection<NovaConcessao> concessoes, UUID concedidoPor) {
		jdbc.sql("DELETE FROM usuario_perfil_escopo WHERE usuario_id = ?").param(usuario).update();
		for (var concessao : concessoes) {
			jdbc.sql("""
					INSERT INTO usuario_perfil_escopo (id, tenant_id, usuario_id, perfil_id, unidade_id, valido_ate,
					                                   concedido_por)
					VALUES (?, app_tenant(), ?, ?, ?, ?, ?)""")
				.params(UUID.randomUUID(), usuario, concessao.perfilId(), concessao.unidadeId(), concessao.validoAte(),
						concedidoPor)
				.update();
		}
	}

	/**
	 * A concessão torna o usuário administrador do tenant: perfil com gestão de usuários
	 * na unidade raiz, sem prazo vencido.
	 */
	public boolean concedeAdministracao(NovaConcessao concessao) {
		return jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM perfil_permissao pp, unidade un
				                WHERE pp.perfil_id = :perfil AND pp.permissao = :permissao
				                  AND un.id = :unidade AND un.ativo AND nlevel(un.caminho) = 1
				                  AND (CAST(:validoAte AS timestamptz) IS NULL
				                       OR CAST(:validoAte AS timestamptz) > now()))""")
			.param("perfil", concessao.perfilId())
			.param("permissao", Permissao.GERIR_USUARIOS)
			.param("unidade", concessao.unidadeId())
			.param("validoAte", concessao.validoAte())
			.query(Boolean.class)
			.single();
	}

	private record ConcessaoComUsuario(UUID usuario, ConcessaoDoUsuario concessao) {
	}

	private static Usuario usuario(ResultSet r) throws SQLException {
		return new Usuario(r.getObject("id", UUID.class), r.getString("nome"), r.getString("email"),
				r.getString("sub_externo"), r.getBoolean("ativo"), r.getObject("ultimo_acesso", OffsetDateTime.class),
				r.getObject("convidado_em", OffsetDateTime.class), List.of());
	}

}
