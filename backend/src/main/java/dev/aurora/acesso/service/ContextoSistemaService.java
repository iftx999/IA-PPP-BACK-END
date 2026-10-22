package dev.aurora.acesso.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.acesso.model.Permissao;
import dev.aurora.acesso.repository.PerfilRepository;
import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

@Service
class ContextoSistemaService implements ContextosDoSistema {

	private static final Logger LOG = LoggerFactory.getLogger(ContextoSistemaService.class);

	private final TenantRepository tenants;

	private final PerfilRepository perfis;

	ContextoSistemaService(TenantRepository tenants, PerfilRepository perfis) {
		this.tenants = tenants;
		this.perfis = perfis;
	}

	@Override
	public List<ContextoAcesso> deTodosOsTenants() {
		var contextos = new ArrayList<ContextoAcesso>();
		for (var tenant : tenants.listarComEmissor()) {
			doTenant(tenant).ifPresent(contextos::add);
		}
		return contextos;
	}

	@Override
	public Optional<ContextoAcesso> doTenant(UUID tenant) {
		try {
			return ContextoAcesso.executar(ContextoAcesso.somenteTenant(tenant), () -> {
				if (!tenants.tenantAtualAtivo()) {
					return Optional.<ContextoAcesso>empty();
				}
				var permissoes = new HashSet<String>();
				perfis.listarCatalogo().stream().map(Permissao::codigo).forEach(permissoes::add);
				return Optional.of(new ContextoAcesso(tenant, null, permissoes, tenants.listarRaizes()));
			});
		}
		catch (Exception erro) {
			LOG.error("Não foi possível montar o contexto do tenant {}", tenant, erro);
			return Optional.empty();
		}
	}

}
