package dev.aurora.contratos.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import dev.aurora.contratos.model.DadosRegra;
import dev.aurora.contratos.service.RegraPenalidadeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Penalidades calculáveis, base do simulador (ADR 0035). */
@RestController
@RequestMapping("/api/v1/contratos")
@ExigePermissao("contrato:ler")
class RegraPenalidadeController {

	private static final String GERENCIAR = "contrato:gerenciar";

	/**
	 * Regra calculável de uma penalidade.
	 *
	 * @param unidadeNome onde a multa é aferida (a unidade do item ou a do contrato)
	 */
	record RegraDePenalidadeResponse(UUID itemId, UUID contratoId, String contratoNumero, String titulo,
			@Nullable String clausula, int pagina, UUID unidadeId, String unidadeNome, FormulaPenalidade formula,
			BigDecimal valor, @Nullable BigDecimal baseValor, @Nullable String baseDescricao, ContagemPontos contagem,
			PeriodicidadePenalidade periodicidade, @Nullable BigDecimal teto, Instant atualizadoEm,
			@Nullable String atualizadoPor) {

		static RegraDePenalidadeResponse de(RegraDePenalidade r) {
			return new RegraDePenalidadeResponse(r.itemId(), r.contratoId(), r.contratoNumero(), r.titulo(),
					r.clausula(), r.pagina(), r.unidadeId(), r.unidadeNome(), r.formula(), r.valor(), r.baseValor(),
					r.baseDescricao(), r.contagem(), r.periodicidade(), r.teto(), r.atualizadoEm(), r.atualizadoPor());
		}
	}

	/**
	 * @param valor percentual por ponto (fórmula percentual) ou reais (demais)
	 * @param baseValor obrigatória só na fórmula percentual
	 */
	record RegraDePenalidadeRequest(@NotNull FormulaPenalidade formula,
			@NotNull @Digits(integer = 13, fraction = 6) BigDecimal valor,
			@Nullable @Digits(integer = 13, fraction = 2) BigDecimal baseValor,
			@Nullable @Size(max = 120) String baseDescricao, @NotNull ContagemPontos contagem,
			@NotNull PeriodicidadePenalidade periodicidade,
			@Nullable @Digits(integer = 13, fraction = 2) BigDecimal teto) {
	}

	private final RegraPenalidadeService regras;

	RegraPenalidadeController(RegraPenalidadeService regras) {
		this.regras = regras;
	}

	@GetMapping("/{contrato}/regras-de-penalidade")
	List<RegraDePenalidadeResponse> listar(@PathVariable UUID contrato) {
		return regras.doContrato(contrato).stream().map(RegraDePenalidadeResponse::de).toList();
	}

	@PutMapping("/itens/{item}/regra-de-penalidade")
	@ExigePermissao(GERENCIAR)
	RegraDePenalidadeResponse definir(@PathVariable UUID item, @Valid @RequestBody RegraDePenalidadeRequest pedido) {
		return RegraDePenalidadeResponse.de(regras.definir(item, new DadosRegra(pedido.formula(), pedido.valor(),
				pedido.baseValor(), pedido.baseDescricao(), pedido.contagem(), pedido.periodicidade(), pedido.teto())));
	}

	@DeleteMapping("/itens/{item}/regra-de-penalidade")
	@ExigePermissao(GERENCIAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remover(@PathVariable UUID item) {
		regras.remover(item);
	}

}
