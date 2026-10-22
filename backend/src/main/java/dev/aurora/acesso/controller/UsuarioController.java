package dev.aurora.acesso.controller;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.acesso.model.ConcessaoDoUsuario;
import dev.aurora.acesso.model.NovaConcessao;
import dev.aurora.acesso.model.SituacaoUsuario;
import dev.aurora.acesso.model.Usuario;
import dev.aurora.acesso.service.GestaoUsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administração de usuários do tenant (H2, H7, H8). */
@RestController
@RequestMapping("/api/v1/usuarios")
@ExigePermissao("usuario:gerenciar")
class UsuarioController {

	record ConcessaoResponse(UUID id, UUID perfilId, String perfil, UUID unidadeId, String unidade,
			@Nullable OffsetDateTime validoAte) {

		static ConcessaoResponse de(ConcessaoDoUsuario c) {
			return new ConcessaoResponse(c.id(), c.perfilId(), c.perfil(), c.unidadeId(), c.unidade(), c.validoAte());
		}
	}

	record UsuarioResponse(UUID id, String nome, String email, SituacaoUsuario situacao,
			@Nullable OffsetDateTime ultimoAcesso, @Nullable OffsetDateTime convidadoEm,
			List<ConcessaoResponse> concessoes) {

		static UsuarioResponse de(Usuario u) {
			return new UsuarioResponse(u.id(), u.nome(), u.email(), u.situacao(), u.ultimoAcesso(), u.convidadoEm(),
					u.concessoes().stream().map(ConcessaoResponse::de).toList());
		}
	}

	record ConcessaoRequest(@NotNull UUID perfilId, @NotNull UUID unidadeId, @Nullable OffsetDateTime validoAte) {

		NovaConcessao dados() {
			return new NovaConcessao(perfilId, unidadeId, validoAte);
		}
	}

	record ConviteRequest(@NotBlank @Size(max = 120) String nome, @NotBlank @Email @Size(max = 254) String email,
			@NotNull @Size(max = 50) List<@Valid @NotNull ConcessaoRequest> concessoes) {
	}

	record ConcessoesRequest(@NotNull @Size(max = 50) List<@Valid @NotNull ConcessaoRequest> concessoes) {
	}

	record SituacaoRequest(boolean ativo) {
	}

	record UsuarioConvidado(UUID id) {
	}

	private final GestaoUsuarioService usuarios;

	UsuarioController(GestaoUsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	@GetMapping
	List<UsuarioResponse> listar() {
		return usuarios.listar().stream().map(UsuarioResponse::de).toList();
	}

	@GetMapping("/{id}")
	UsuarioResponse detalhar(@PathVariable UUID id) {
		return UsuarioResponse.de(usuarios.buscar(id));
	}

	/** Convida por e-mail: o link é de uso único e expira (RN-17). */
	@PostMapping
	ResponseEntity<UsuarioConvidado> convidar(@Valid @RequestBody ConviteRequest pedido) {
		var id = usuarios.convidar(new GestaoUsuarioService.DadosConvite(pedido.nome(), pedido.email(),
				pedido.concessoes().stream().map(ConcessaoRequest::dados).toList()));
		return ResponseEntity.created(URI.create("/api/v1/usuarios/" + id)).body(new UsuarioConvidado(id));
	}

	@PostMapping("/{id}/convite")
	ResponseEntity<Void> reenviarConvite(@PathVariable UUID id) {
		usuarios.reenviarConvite(id);
		return ResponseEntity.accepted().build();
	}

	@PutMapping("/{id}/concessoes")
	ResponseEntity<Void> alterarConcessoes(@PathVariable UUID id, @Valid @RequestBody ConcessoesRequest pedido) {
		usuarios.alterarConcessoes(id, pedido.concessoes().stream().map(ConcessaoRequest::dados).toList());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/situacao")
	ResponseEntity<Void> definirSituacao(@PathVariable UUID id, @RequestBody SituacaoRequest pedido) {
		usuarios.definirAtivo(id, pedido.ativo());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/redefinicao-senha")
	ResponseEntity<Void> redefinirSenha(@PathVariable UUID id) {
		usuarios.enviarRedefinicaoSenha(id);
		return ResponseEntity.accepted().build();
	}

}
