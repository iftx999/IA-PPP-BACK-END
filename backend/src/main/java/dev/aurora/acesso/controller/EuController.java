package dev.aurora.acesso.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.acesso.model.UsuarioAutenticado;
import dev.aurora.acesso.service.UsuarioService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do usuário autenticado, para o cabeçalho e o seletor de escopo. */
@RestController
@RequestMapping("/api/v1/eu")
class EuController {

	record EuResponse(UUID usuarioId, String nome, String email, UUID tenantId, String tenant,
			List<String> permissoes) {

		static EuResponse de(UsuarioAutenticado u) {
			return new EuResponse(u.usuarioId(), u.nome(), u.email(), u.tenantId(), u.tenant(), u.permissoes());
		}
	}

	private final UsuarioService usuarios;

	EuController(UsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	@GetMapping
	EuResponse eu() {
		return EuResponse.de(usuarios.usuarioAtual());
	}

}
