package dev.aurora.adesao.controller;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.service.PainelService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/painel")
class PainelController {

	private final PainelService painel;

	PainelController(PainelService painel) {
		this.painel = painel;
	}

	/**
	 * @param unidade unidade do escopo (padrão: a mais alta)
	 * @param competencia mês no formato yyyy-MM (padrão: o mais recente com medição)
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('indicador:ler')")
	PainelResponse painel(@RequestParam Optional<UUID> unidade,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") Optional<YearMonth> competencia) {
		return PainelResponse.de(painel.montar(unidade, competencia));
	}

}
