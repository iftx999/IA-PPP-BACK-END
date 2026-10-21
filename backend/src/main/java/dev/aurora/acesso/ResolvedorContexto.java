package dev.aurora.acesso;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Traduz um token válido em {@link ContextoAcesso}: emissor -> tenant, sub (ou e-mail, no primeiro acesso) -> usuário,
 * concessões vigentes -> permissões e escopo.
 */
@Component
class ResolvedorContexto {

	private final JdbcClient jdbc;

	ResolvedorContexto(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<UUID> tenantDoEmissor(String emissor) {
		return jdbc.sql("SELECT tenant_id FROM tenant_emissor WHERE emissor = ?")
			.param(emissor)
			.query(UUID.class)
			.optional();
	}

	Optional<ContextoAcesso> resolver(Jwt token) throws Exception {
		var tenantId = tenantDoEmissor(String.valueOf(token.getIssuer()));
		if (tenantId.isEmpty()) {
			return Optional.empty();
		}
		return ContextoAcesso.executar(ContextoAcesso.somenteTenant(tenantId.get()),
				() -> resolverNoTenant(tenantId.get(), token));
	}

	private Optional<ContextoAcesso> resolverNoTenant(UUID tenantId, Jwt token) {
		var tenantAtivo = jdbc.sql("SELECT ativo FROM tenant").query(Boolean.class).optional().orElse(false);
		if (!tenantAtivo) {
			return Optional.empty();
		}
		var usuarioId = usuarioDoToken(token);
		if (usuarioId.isEmpty()) {
			return Optional.empty();
		}

		var permissoes = new HashSet<String>();
		var escopos = new LinkedHashSet<String>();
		jdbc.sql("""
				SELECT pp.permissao, u.caminho::text AS caminho
				  FROM usuario_perfil_escopo upe
				  JOIN perfil_permissao pp ON pp.perfil_id = upe.perfil_id
				  JOIN unidade u ON u.id = upe.unidade_id AND u.ativo
				 WHERE upe.usuario_id = ?
				   AND (upe.valido_ate IS NULL OR upe.valido_ate > now())""")
			.param(usuarioId.get())
			.query((linha, n) -> {
				permissoes.add(linha.getString("permissao"));
				escopos.add(linha.getString("caminho"));
				return null;
			})
			.list();

		jdbc.sql("""
				UPDATE usuario SET ultimo_acesso = now()
				 WHERE id = ? AND (ultimo_acesso IS NULL OR ultimo_acesso < now() - interval '5 minutes')""")
			.param(usuarioId.get())
			.update();

		return Optional.of(new ContextoAcesso(tenantId, usuarioId.get(), permissoes, List.copyOf(escopos)));
	}

	/** Usuário ativo pelo sub; no primeiro acesso de um convidado, vincula o sub pelo e-mail verificado. */
	private Optional<UUID> usuarioDoToken(Jwt token) {
		var porSub = jdbc.sql("SELECT id FROM usuario WHERE sub_externo = ? AND ativo")
			.param(token.getSubject())
			.query(UUID.class)
			.optional();
		if (porSub.isPresent()) {
			return porSub;
		}
		String email = token.getClaimAsString("email");
		if (email == null || !Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))) {
			return Optional.empty();
		}
		return jdbc.sql("""
				UPDATE usuario SET sub_externo = ?
				 WHERE lower(email) = lower(?) AND sub_externo IS NULL AND ativo
				RETURNING id""")
			.param(token.getSubject())
			.param(email)
			.query(UUID.class)
			.optional();
	}

}
