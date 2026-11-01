package dev.aurora.acesso.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.acesso.model.UsuarioAutenticado;
import dev.aurora.acesso.service.GestaoUsuarioService;
import dev.aurora.acesso.service.UsuarioService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do usuário autenticado, para o cabeçalho e o seletor de escopo. */
@RestController
@RequestMapping("/api/v1/eu")
class EuController {

	/**
	 * @param tenantCodigo código do cliente (tela de entrada e logo em
	 * /api/v1/publico/clientes/{codigo}/logo)
	 */
	record EuResponse(UUID usuarioId, String nome, String email, UUID tenantId, String tenant, String tenantCodigo,
			boolean tenantTemLogo, List<String> permissoes) {

		static EuResponse de(UsuarioAutenticado u) {
			return new EuResponse(u.usuarioId(), u.nome(), u.email(), u.tenantId(), u.tenant(), u.tenantCodigo(),
					u.tenantTemLogo(), u.permissoes());
		}
	}

	private final UsuarioService usuarios;

	private final GestaoUsuarioService gestao;

	EuController(UsuarioService usuarios, GestaoUsuarioService gestao) {
		this.usuarios = usuarios;
		this.gestao = gestao;
	}

	@GetMapping
	EuResponse eu() {
		return EuResponse.de(usuarios.usuarioAtual());
	}

	/** "Sair de todos os dispositivos" (doc 08, 8.6): encerra as sessões no provedor. */
	@DeleteMapping("/sessoes")
	ResponseEntity<Void> encerrarSessoes() {
		gestao.encerrarMinhasSessoes();
		return ResponseEntity.noContent().build();
	}

}
