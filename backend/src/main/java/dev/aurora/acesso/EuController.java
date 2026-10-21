package dev.aurora.acesso;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados do usuário autenticado, para o cabeçalho e o seletor de escopo. */
@RestController
class EuController {

	record Eu(UUID usuarioId, String nome, String email, UUID tenantId, String tenant, List<String> permissoes) {
	}

	private final JdbcClient jdbc;

	EuController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/v1/eu")
	Eu eu() {
		var contexto = ContextoAcesso.exigir();
		return jdbc.sql("""
				SELECT u.nome, u.email, t.nome AS tenant
				  FROM usuario u JOIN tenant t ON t.id = u.tenant_id
				 WHERE u.id = ?""")
			.param(contexto.usuarioId())
			.query((linha, n) -> new Eu(contexto.usuarioId(), linha.getString("nome"), linha.getString("email"),
					contexto.tenantId(), linha.getString("tenant"), contexto.permissoes().stream().sorted().toList()))
			.single();
	}

}
