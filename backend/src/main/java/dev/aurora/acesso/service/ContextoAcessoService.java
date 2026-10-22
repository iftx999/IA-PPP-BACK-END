package dev.aurora.acesso.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.acesso.model.Concessao;
import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.acesso.repository.UsuarioRepository;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Traduz um token válido em {@link ContextoAcesso}: emissor -> tenant, sub (ou e-mail
 * verificado, no primeiro acesso) -> usuário, concessões vigentes -> permissões e escopo.
 */
@Service
public class ContextoAcessoService {

	private final TenantRepository tenants;

	private final UsuarioRepository usuarios;

	ContextoAcessoService(TenantRepository tenants, UsuarioRepository usuarios) {
		this.tenants = tenants;
		this.usuarios = usuarios;
	}

	public Optional<UUID> tenantDoEmissor(String emissor) {
		return tenants.buscarPorEmissor(emissor);
	}

	/**
	 * Vazio quando o emissor é desconhecido, o tenant está inativo ou a pessoa não é
	 * usuária ativa.
	 */
	public Optional<ContextoAcesso> resolver(Jwt token) throws Exception {
		var tenantId = tenantDoEmissor(String.valueOf(token.getIssuer()));
		if (tenantId.isEmpty()) {
			return Optional.empty();
		}
		return ContextoAcesso.executar(ContextoAcesso.somenteTenant(tenantId.get()),
				() -> resolverNoTenant(tenantId.get(), token));
	}

	private Optional<ContextoAcesso> resolverNoTenant(UUID tenantId, Jwt token) {
		if (!tenants.tenantAtualAtivo()) {
			return Optional.empty();
		}
		var usuarioId = identificarUsuario(token);
		if (usuarioId.isEmpty()) {
			return Optional.empty();
		}

		// Onde cada permissão vale; o escopo inicial é a união (restringido por
		// @Permissao).
		var concessoes = new TreeMap<String, Set<String>>();
		var escopos = new LinkedHashSet<String>();
		for (Concessao concessao : usuarios.listarConcessoesVigentes(usuarioId.get())) {
			concessoes.computeIfAbsent(concessao.permissao(), p -> new LinkedHashSet<>())
				.add(concessao.caminhoUnidade());
			escopos.add(concessao.caminhoUnidade());
		}
		usuarios.registrarAcesso(usuarioId.get());
		var porPermissao = new TreeMap<String, List<String>>();
		concessoes.forEach((permissao, caminhos) -> porPermissao.put(permissao, List.copyOf(caminhos)));
		return Optional.of(new ContextoAcesso(tenantId, usuarioId.get(), porPermissao, List.copyOf(escopos)));
	}

	/**
	 * Pelo sub; no primeiro acesso de um convidado, vincula o sub pelo e-mail, desde que
	 * verificado pelo IdP.
	 */
	private Optional<UUID> identificarUsuario(Jwt token) {
		var porSub = usuarios.buscarAtivoPorSub(token.getSubject());
		if (porSub.isPresent()) {
			return porSub;
		}
		String email = token.getClaimAsString("email");
		if (email == null || !Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))) {
			return Optional.empty();
		}
		return usuarios.vincularSubPorEmail(token.getSubject(), email);
	}

}
