package dev.aurora.campanhas.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.service.DispositivoService;
import dev.aurora.contexto.ExigePermissao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Celulares do app de campo (J13; ADR 0045): ver e bloquear. */
@RestController
@RequestMapping("/api/v1/dispositivos")
@ExigePermissao("dispositivo:gerenciar")
class DispositivoController {

	private final DispositivoService dispositivos;

	DispositivoController(DispositivoService dispositivos) {
		this.dispositivos = dispositivos;
	}

	@GetMapping
	List<DispositivoResponse> listar() {
		return dispositivos.listar().stream().map(DispositivoResponse::de).toList();
	}

	/** Bloqueia o celular: perde o acesso e apaga os dados locais na próxima conexão. */
	@PostMapping("/{id}/revogar")
	DispositivoResponse revogar(@PathVariable UUID id) {
		return DispositivoResponse.de(dispositivos.revogar(id));
	}

}
