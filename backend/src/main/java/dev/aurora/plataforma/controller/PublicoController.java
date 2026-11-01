package dev.aurora.plataforma.controller;

import java.time.Duration;

import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.plataforma.service.ConsultaPublicaService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sem login (ADR 0027): a tela de entrada descobre pelo código da empresa onde a pessoa
 * entra. Código inexistente, inativo ou incompleto responde 404, sem distinguir.
 */
@RestController
@RequestMapping("/api/v1/publico/clientes")
class PublicoController {

	private final ConsultaPublicaService consulta;

	PublicoController(ConsultaPublicaService consulta) {
		this.consulta = consulta;
	}

	@GetMapping("/{codigo}")
	ConsultaPublicaService.ClientePublico porCodigo(@PathVariable String codigo) {
		return consulta.porCodigo(codigo).orElseThrow(RecursoNaoEncontradoException::new);
	}

	@GetMapping("/{codigo}/logo")
	ResponseEntity<byte[]> logo(@PathVariable String codigo) {
		var logo = consulta.logo(codigo).orElseThrow(RecursoNaoEncontradoException::new);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(logo.tipo()))
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
			// A imagem nunca é interpretada como outra coisa (conferida pelo conteúdo no
			// envio).
			.header("X-Content-Type-Options", "nosniff")
			.body(logo.conteudo());
	}

}
