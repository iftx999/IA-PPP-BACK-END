package dev.aurora.metas.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.metas.model.NovaMeta;
import dev.aurora.metas.service.MetaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metas")
class MetaController {

	/** Percentuais com até 2 casas, de 0 a 100. */
	record NovaMetaRequest(@NotNull UUID unidadeId,
			@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3,
					fraction = 2) BigDecimal valor,
			@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal alerta,
			@NotNull LocalDate vigenciaInicio, @Nullable LocalDate vigenciaFim) {
	}

	record EncerramentoRequest(@NotNull LocalDate fim) {
	}

	record MetaCriada(UUID id) {
	}

	private final MetaService metas;

	MetaController(MetaService metas) {
		this.metas = metas;
	}

	@GetMapping
	@ExigePermissao("indicador:ler")
	MetasResponse listar() {
		return MetasResponse.de(metas.arvore());
	}

	@PostMapping
	@ExigePermissao("indicador:gerenciar")
	ResponseEntity<MetaCriada> criar(@Valid @RequestBody NovaMetaRequest pedido) {
		var id = metas.criar(new NovaMeta(pedido.unidadeId(), pedido.valor(), pedido.alerta(), pedido.vigenciaInicio(),
				pedido.vigenciaFim()));
		return ResponseEntity.created(URI.create("/api/v1/metas/" + id)).body(new MetaCriada(id));
	}

	@PostMapping("/{id}/encerramento")
	@ExigePermissao("indicador:gerenciar")
	ResponseEntity<Void> encerrar(@PathVariable UUID id, @Valid @RequestBody EncerramentoRequest pedido) {
		metas.encerrar(id, pedido.fim());
		return ResponseEntity.noContent().build();
	}

}
