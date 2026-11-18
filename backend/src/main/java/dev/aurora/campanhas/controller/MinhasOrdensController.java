package dev.aurora.campanhas.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.service.OrdemServicoService;
import dev.aurora.campanhas.service.ProgramacaoService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OS das equipes de que a pessoa faz parte (ADR 0036, 0044). */
@RestController
@RequestMapping("/api/v1/minhas-ordens")
@ExigePermissao("os:executar")
class MinhasOrdensController {

	private final OrdemServicoService ordens;

	private final ProgramacaoService programacao;

	MinhasOrdensController(OrdemServicoService ordens, ProgramacaoService programacao) {
		this.ordens = ordens;
		this.programacao = programacao;
	}

	/**
	 * Com a equipe primeiro (data, prioridade e prazo), depois as encerradas e devolvidas
	 * dos últimos 30 dias.
	 */
	@GetMapping
	List<OrdemServicoResponse> listar() {
		return ordens.minhas().stream().map(OrdemServicoResponse::de).toList();
	}

	/** Motivos de devolução e de não execução aceitos. */
	@GetMapping("/motivos")
	List<MotivoDaOrdemResponse> motivos() {
		return programacao.opcoes()
			.motivos()
			.stream()
			.filter(m -> "DEVOLUCAO".equals(m.tipo()) || "NAO_EXECUCAO".equals(m.tipo()))
			.map(MotivoDaOrdemResponse::de)
			.toList();
	}

	@PutMapping("/{id}/resultado")
	OrdemServicoResponse encerrar(@PathVariable UUID id, @Valid @RequestBody ResultadoRequest pedido) {
		return OrdemServicoResponse.de(ordens.encerrarPelaEquipe(id, pedido.registro()));
	}

	/** Volta para a programação, com o motivo (D8). */
	@PostMapping("/{id}/devolver")
	OrdemServicoResponse devolver(@PathVariable UUID id, @Valid @RequestBody JustificativaRequest pedido) {
		return OrdemServicoResponse.de(ordens.devolver(id, pedido.justificativa()));
	}

}
