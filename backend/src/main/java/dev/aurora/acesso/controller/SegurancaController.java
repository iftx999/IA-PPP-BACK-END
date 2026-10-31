package dev.aurora.acesso.controller;

import dev.aurora.acesso.model.ProvedorCorporativo;
import dev.aurora.acesso.model.ProvedorCorporativo.Protocolo;
import dev.aurora.acesso.service.PoliticaSegundoFatorService;
import dev.aurora.acesso.service.SsoCorporativoService;
import dev.aurora.contexto.ExigePermissao;
import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Política de acesso do tenant: verificação em duas etapas (S9, RN-17) e SSO corporativo
 * (S10).
 */
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

	/**
	 * IdP corporativo. O segredo do client nunca é devolvido.
	 *
	 * @param provedor vazio se o tenant não tem SSO
	 */
	record SsoResponse(@Nullable ProvedorResponse provedor, boolean confiaSegundoFator, String enderecoRetorno,
			String entidadeSaml, String metadadosSaml) {

		static SsoResponse de(SsoCorporativoService.Configuracao c) {
			return new SsoResponse(c.provedor().map(ProvedorResponse::de).orElse(null), c.confiaSegundoFator(),
					c.enderecoRetorno(), c.entidadeSaml(), c.metadadosSaml());
		}
	}

	record ProvedorResponse(Protocolo protocolo, String nome, String endereco, @Nullable String clienteId,
			boolean habilitado) {

		static ProvedorResponse de(ProvedorCorporativo p) {
			return new ProvedorResponse(p.protocolo(), p.nome(), p.endereco(), p.clienteId(), p.habilitado());
		}
	}

	/**
	 * @param segredo client secret (OIDC); vazio mantém o atual
	 */
	record SsoRequest(Protocolo protocolo, String nome, String endereco, @Nullable String clienteId,
			@Nullable String segredo, boolean habilitado, boolean confiaSegundoFator) {
	}

	private final PoliticaSegundoFatorService politica;

	private final SsoCorporativoService sso;

	SegurancaController(PoliticaSegundoFatorService politica, SsoCorporativoService sso) {
		this.politica = politica;
		this.sso = sso;
	}

	@GetMapping("/sso")
	SsoResponse obterSso() {
		return SsoResponse.de(sso.obter());
	}

	@PutMapping("/sso")
	SsoResponse salvarSso(@RequestBody SsoRequest pedido) {
		return SsoResponse
			.de(sso.salvar(new SsoCorporativoService.Alteracao(pedido.protocolo(), pedido.nome(), pedido.endereco(),
					pedido.clienteId(), pedido.segredo(), pedido.habilitado(), pedido.confiaSegundoFator())));
	}

	@DeleteMapping("/sso")
	SsoResponse removerSso() {
		return SsoResponse.de(sso.remover());
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
