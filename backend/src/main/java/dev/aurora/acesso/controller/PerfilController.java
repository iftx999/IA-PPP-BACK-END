package dev.aurora.acesso.controller;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.aurora.acesso.model.Perfil;
import dev.aurora.acesso.model.Permissao;
import dev.aurora.acesso.service.PerfilService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('usuario:gerenciar')")
class PerfilController {

	record PermissaoResponse(String codigo, String descricao, String area) {

		static PermissaoResponse de(Permissao p) {
			return new PermissaoResponse(p.codigo(), p.descricao(), p.area());
		}
	}

	record PerfilResponse(UUID id, String nome, @Nullable String descricao, boolean sistema, List<String> permissoes,
			long usuarios) {

		static PerfilResponse de(Perfil p) {
			return new PerfilResponse(p.id(), p.nome(), p.descricao(), p.sistema(), p.permissoes(), p.usuarios());
		}
	}

	record PerfilRequest(@NotBlank @Size(max = 80) String nome, @Nullable @Size(max = 300) String descricao,
			@NotNull Set<@NotBlank String> permissoes) {

		PerfilService.DadosPerfil dados() {
			return new PerfilService.DadosPerfil(nome, descricao, permissoes);
		}
	}

	record PerfilCriado(UUID id) {
	}

	private final PerfilService perfis;

	PerfilController(PerfilService perfis) {
		this.perfis = perfis;
	}

	@GetMapping("/api/v1/permissoes")
	List<PermissaoResponse> catalogo() {
		return perfis.catalogo().stream().map(PermissaoResponse::de).toList();
	}

	@GetMapping("/api/v1/perfis")
	List<PerfilResponse> listar() {
		return perfis.listar().stream().map(PerfilResponse::de).toList();
	}

	@PostMapping("/api/v1/perfis")
	ResponseEntity<PerfilCriado> criar(@Valid @RequestBody PerfilRequest pedido) {
		var id = perfis.criar(pedido.dados());
		return ResponseEntity.created(URI.create("/api/v1/perfis/" + id)).body(new PerfilCriado(id));
	}

	@PutMapping("/api/v1/perfis/{id}")
	ResponseEntity<Void> alterar(@PathVariable UUID id, @Valid @RequestBody PerfilRequest pedido) {
		perfis.alterar(id, pedido.dados());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/perfis/{id}")
	ResponseEntity<Void> remover(@PathVariable UUID id) {
		perfis.remover(id);
		return ResponseEntity.noContent().build();
	}

}
