package dev.aurora.integracoes.controller;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.aurora.integracoes.config.AgenteAutenticado;
import dev.aurora.integracoes.model.RegistroCarga;
import dev.aurora.integracoes.model.Rejeicao;
import dev.aurora.integracoes.model.ResultadoCarga;
import dev.aurora.integracoes.service.CargaService;
import dev.aurora.integracoes.service.IntegracaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do Agente Aurora (RN-09): o agente roda na rede da concessionária e envia só
 * matrícula, unidade e situação. Autenticado pela credencial da fonte (FiltroAgente).
 */
@RestController
@RequestMapping("/api/agente/v1")
class AgenteController {

	record RegistroAgente(@Nullable String matricula, @Nullable String unidade, @Nullable String situacao) {
	}

	record CargaAgenteRequest(@NotNull YearMonth competencia,
			@NotNull @Size(min = 1, max = 500_000) List<@NotNull RegistroAgente> registros) {
	}

	record ResultadoCargaResponse(UUID execucao, YearMonth competencia, long lidos, long novos, long atualizados,
			long inalterados, long rejeitados, List<Rejeicao> rejeicoes, int medicoesRecalculadas) {

		static ResultadoCargaResponse de(ResultadoCarga r) {
			return new ResultadoCargaResponse(r.execucaoId(), r.competencia(), r.lidos(), r.novos(), r.atualizados(),
					r.inalterados(), r.rejeitados(), r.rejeicoes(), r.medicoes());
		}
	}

	record SituacaoAgenteResponse(UUID fonte, String nome, boolean ativa, @Nullable Instant ultimaCarga) {
	}

	private final CargaService cargas;

	private final IntegracaoService integracoes;

	AgenteController(CargaService cargas, IntegracaoService integracoes) {
		this.cargas = cargas;
		this.integracoes = integracoes;
	}

	/** Para o agente conferir a conexão e a credencial. */
	@GetMapping("/situacao")
	SituacaoAgenteResponse situacao(HttpServletRequest requisicao) {
		var fonte = integracoes.buscar(AgenteAutenticado.fonte(requisicao));
		return new SituacaoAgenteResponse(fonte.id(), fonte.nome(), fonte.ativo(), fonte.ultimaCarga());
	}

	/**
	 * Carga da competência; linhas inválidas são recusadas com motivo, sem derrubar a
	 * carga.
	 */
	@PostMapping("/cargas")
	ResultadoCargaResponse carregar(@Valid @RequestBody CargaAgenteRequest pedido, HttpServletRequest requisicao) {
		var linha = new AtomicInteger();
		var registros = pedido.registros()
			.stream()
			.map(r -> new RegistroCarga(linha.incrementAndGet(), r.matricula(), r.unidade(), r.situacao()))
			.toList();
		return ResultadoCargaResponse.de(cargas.carregar(AgenteAutenticado.fonte(requisicao), pedido.competencia(),
				registros, CargaService.Origem.AGENTE, AgenteAutenticado.prefixo(requisicao)));
	}

}
