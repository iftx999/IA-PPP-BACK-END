package dev.aurora.alertas.controller;

import dev.aurora.alertas.model.PreferenciaEmail;
import dev.aurora.alertas.service.PreferenciaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Preferência de e-mail dos alertas: cada pessoa define a própria, sem permissão
 * específica (recebe só alertas que já pode ver).
 */
@RestController
@RequestMapping("/api/v1/alertas/preferencias")
class PreferenciaController {

	record PreferenciasResponse(PreferenciaEmail email, boolean envioLigado) {

		static PreferenciasResponse de(PreferenciaService.Preferencias p) {
			return new PreferenciasResponse(p.email(), p.envioLigado());
		}
	}

	record PreferenciasRequest(@NotNull PreferenciaEmail email) {
	}

	private final PreferenciaService preferencias;

	PreferenciaController(PreferenciaService preferencias) {
		this.preferencias = preferencias;
	}

	@GetMapping
	PreferenciasResponse minhas() {
		return PreferenciasResponse.de(preferencias.minhas());
	}

	@PutMapping
	PreferenciasResponse definir(@Valid @RequestBody PreferenciasRequest pedido) {
		return PreferenciasResponse.de(preferencias.definir(pedido.email()));
	}

}
