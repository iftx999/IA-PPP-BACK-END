package dev.aurora.mapa.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.mapa.controller.MapaResponses.MarcacaoDto;
import dev.aurora.mapa.model.NovaMarcacao;
import dev.aurora.mapa.service.MarcacaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E4: marcar visita no mapa (ponto, texto, autor e data). */
@RestController
@RequestMapping("/api/v1/mapa/marcacoes")
class MarcacaoController {

	record NovaMarcacaoRequest(@DecimalMin("-75") @DecimalMax("-28") double longitude,
			@DecimalMin("-35") @DecimalMax("7") double latitude, @NotBlank @Size(max = 80) String titulo,
			@Size(max = 500) @Nullable String observacao) {
	}

	private final MarcacaoService marcacoes;

	MarcacaoController(MarcacaoService marcacoes) {
		this.marcacoes = marcacoes;
	}

	/** Marcações da área visível (as 500 mais recentes). */
	@GetMapping
	@ExigePermissao("indicador:ler")
	List<MarcacaoDto> listar(@Valid @ModelAttribute AreaParametro area) {
		var usuario = ContextoAcesso.exigir().usuarioId();
		return marcacoes.listar(area.area()).stream().map(m -> MarcacaoDto.de(m, usuario)).toList();
	}

	@PostMapping
	@ExigePermissao("mapa:marcar")
	ResponseEntity<MarcacaoDto> criar(@Valid @RequestBody NovaMarcacaoRequest pedido) {
		var criada = marcacoes
			.criar(new NovaMarcacao(pedido.longitude(), pedido.latitude(), pedido.titulo(), pedido.observacao()));
		return ResponseEntity.created(URI.create("/api/v1/mapa/marcacoes/" + criada.id()))
			.body(MarcacaoDto.de(criada, ContextoAcesso.exigir().usuarioId()));
	}

	@DeleteMapping("/{id}")
	@ExigePermissao("mapa:marcar")
	ResponseEntity<Void> remover(@PathVariable UUID id) {
		marcacoes.remover(id);
		return ResponseEntity.noContent().build();
	}

}
