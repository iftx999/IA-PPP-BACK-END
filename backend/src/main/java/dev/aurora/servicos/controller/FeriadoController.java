package dev.aurora.servicos.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.servicos.model.Feriado;
import dev.aurora.servicos.service.FeriadoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Feriados que contam no prazo das OS (RN-21, P6, ADR 0044). */
@RestController
@RequestMapping("/api/v1/catalogo/feriados")
@ExigePermissao("servico:gerenciar")
class FeriadoController {

	/**
	 * @param nacional calculado pela Aurora: não pode ser removido
	 * @param unidadeId nulo nos nacionais
	 */
	record FeriadoResponse(@Nullable UUID id, @Nullable UUID unidadeId, String unidadeNome, LocalDate data, String nome,
			boolean nacional) {

		static FeriadoResponse de(Feriado f) {
			return f.nacional() ? new FeriadoResponse(null, null, f.unidadeNome(), f.data(), f.nome(), true)
					: new FeriadoResponse(f.id(), f.unidadeId(), f.unidadeNome(), f.data(), f.nome(), false);
		}
	}

	record FeriadoRequest(@NotNull UUID unidadeId, @NotNull LocalDate data, @NotNull @Size(max = 80) String nome) {
	}

	private final FeriadoService feriados;

	FeriadoController(FeriadoService feriados) {
		this.feriados = feriados;
	}

	@GetMapping
	List<FeriadoResponse> doAno(@RequestParam @Min(2000) @Max(2100) int ano) {
		return feriados.doAno(ano).stream().map(FeriadoResponse::de).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	FeriadoResponse cadastrar(@Valid @RequestBody FeriadoRequest pedido) {
		return FeriadoResponse.de(feriados.cadastrar(pedido.unidadeId(), pedido.data(), pedido.nome()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remover(@PathVariable UUID id) {
		feriados.remover(id);
	}

}
