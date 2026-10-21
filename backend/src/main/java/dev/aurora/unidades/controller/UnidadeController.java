package dev.aurora.unidades.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.unidades.model.Unidade;
import dev.aurora.unidades.service.UnidadeService;
import org.jspecify.annotations.Nullable;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/unidades")
class UnidadeController {

	record UnidadeResponse(UUID id, String tipo, @Nullable UUID unidadePaiId, String codigo, String nome,
			@Nullable String codigoIbge) {

		static UnidadeResponse de(Unidade u) {
			return new UnidadeResponse(u.id(), u.tipo(), u.unidadePaiId(), u.codigo(), u.nome(), u.codigoIbge());
		}
	}

	private final UnidadeService unidades;

	UnidadeController(UnidadeService unidades) {
		this.unidades = unidades;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('unidade:ler')")
	List<UnidadeResponse> listar() {
		return unidades.listar().stream().map(UnidadeResponse::de).toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('unidade:ler')")
	UnidadeResponse detalhar(@PathVariable UUID id) {
		return UnidadeResponse.de(unidades.buscar(id));
	}

}
