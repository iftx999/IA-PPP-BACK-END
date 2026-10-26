package dev.aurora.acesso.service;

import java.util.Map;

import dev.aurora.acesso.repository.TenantRepository;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Política de verificação em duas etapas do tenant (S9, RN-17). Vale na próxima
 * requisição de cada pessoa: quem estiver sem o código entra de novo.
 */
@Service
public class PoliticaSegundoFatorService {

	private final TenantRepository tenants;

	private final SegundoFatorService segundoFator;

	private final Auditoria auditoria;

	PoliticaSegundoFatorService(TenantRepository tenants, SegundoFatorService segundoFator, Auditoria auditoria) {
		this.tenants = tenants;
		this.segundoFator = segundoFator;
		this.auditoria = auditoria;
	}

	/**
	 * @param todos exigido de todos os usuários do tenant
	 * @param administradores exigido de quem gerencia usuários (configuração da
	 * plataforma, não do tenant)
	 */
	public record Politica(boolean todos, boolean administradores) {
	}

	@Transactional(readOnly = true)
	public Politica obter() {
		EscopoAdministrativo.exigirTenantInteiro();
		return new Politica(tenants.segundoFatorObrigatorio(), segundoFator.exigidoDeAdministradores());
	}

	@Transactional
	public Politica definir(boolean todos) {
		var contexto = EscopoAdministrativo.exigirTenantInteiro();
		if (tenants.segundoFatorObrigatorio() != todos) {
			tenants.definirSegundoFatorObrigatorio(todos);
			auditoria.registrar(TipoEvento.POLITICA_SEGUNDO_FATOR_ALTERADA, "tenant/" + contexto.tenantId(),
					Map.of("todos", todos));
		}
		return new Politica(todos, segundoFator.exigidoDeAdministradores());
	}

}
