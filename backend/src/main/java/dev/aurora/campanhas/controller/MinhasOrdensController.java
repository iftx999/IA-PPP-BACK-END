package dev.aurora.campanhas.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.service.OrdemServicoService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OS das equipes de que a pessoa faz parte (I3). */
@RestController
@RequestMapping("/api/v1/minhas-ordens")
@ExigePermissao("os:executar")
class MinhasOrdensController {

	private final OrdemServicoService ordens;

	MinhasOrdensController(OrdemServicoService ordens) {
		this.ordens = ordens;
	}

	/**
	 * Em campo primeiro (prioridade e prazo), depois os resultados dos últimos 30 dias.
	 */
	@GetMapping
	List<OrdemServicoResponse> listar() {
		return ordens.minhas().stream().map(OrdemServicoResponse::de).toList();
	}

	@PutMapping("/{id}/resultado")
	OrdemServicoResponse registrar(@PathVariable UUID id, @Valid @RequestBody ResultadoRequest pedido) {
		return OrdemServicoResponse.de(ordens.registrarPelaEquipe(id, pedido.registro()));
	}

}
