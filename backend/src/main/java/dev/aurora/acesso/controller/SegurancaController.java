package dev.aurora.acesso.controller;

import dev.aurora.acesso.service.PoliticaSegundoFatorService;
import dev.aurora.contexto.ExigePermissao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Política de acesso do tenant: verificação em duas etapas (S9, RN-17). */
@RestController
@RequestMapping("/api/v1/seguranca")
@ExigePermissao("usuario:gerenciar")
class SegurancaController {

	record PoliticaSegundoFatorResponse(boolean todos, boolean administradores) {

		static PoliticaSegundoFatorResponse de(PoliticaSegundoFatorService.Politica p) {
			return new PoliticaSegundoFatorResponse(p.todos(), p.administradores());
		}
	}

	record PoliticaSegundoFatorRequest(boolean todos) {
	}

	private final PoliticaSegundoFatorService politica;

	SegurancaController(PoliticaSegundoFatorService politica) {
		this.politica = politica;
	}

	@GetMapping("/segundo-fator")
	PoliticaSegundoFatorResponse obter() {
		return PoliticaSegundoFatorResponse.de(politica.obter());
	}

	@PutMapping("/segundo-fator")
	PoliticaSegundoFatorResponse definir(@RequestBody PoliticaSegundoFatorRequest pedido) {
		return PoliticaSegundoFatorResponse.de(politica.definir(pedido.todos()));
	}

}
