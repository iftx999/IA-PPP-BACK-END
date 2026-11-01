package dev.aurora.plataforma.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.aurora.plataforma.model.Limites;
import dev.aurora.plataforma.service.ClienteService;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administração da plataforma (S1, ADR 0027). Só o realm da plataforma entra aqui (cadeia
 * de segurança própria); tokens de clientes são recusados.
 */
@RestController
@RequestMapping("/api/v1/plataforma/clientes")
class PlataformaController {

	record LimitesDto(@Nullable Integer usuarios, @Nullable Long assistenteTokensPorMes,
			@Nullable BigDecimal assistenteUsdPorMes) {

		Limites modelo() {
			return new Limites(usuarios, assistenteTokensPorMes, assistenteUsdPorMes);
		}
	}

	/**
	 * @param provisionado falso enquanto realm, perfis ou convite do primeiro
	 * administrador não terminaram
	 */
	record ClienteResponse(UUID id, String codigo, String nome, boolean ativo, Instant criadoEm,
			@Nullable String emissor, @Nullable String contatoNome, @Nullable String contatoEmail, LimitesDto limites,
			long usuariosAtivos, boolean temLogo, boolean provisionado) {

		static ClienteResponse de(ClienteService.Resumo r) {
			var c = r.cliente();
			return new ClienteResponse(c.id(), c.codigo(), c.nome(), c.ativo(), c.criadoEm(), c.emissor(),
					c.contatoNome(), c.contatoEmail(),
					new LimitesDto(c.limiteUsuarios(), c.assistenteTokensPorMes(), c.assistenteUsdPorMes()),
					r.usuariosAtivos(), c.temLogo(), c.provisionadoEm() != null);
		}
	}

	record NovoClienteRequest(String codigo, String nome, String contatoNome, String contatoEmail,
			@Nullable LimitesDto limites) {
	}

	record AlteracaoClienteRequest(String nome, boolean ativo, @Nullable LimitesDto limites) {
	}

	private final ClienteService clientes;

	PlataformaController(ClienteService clientes) {
		this.clientes = clientes;
	}

	@GetMapping
	List<ClienteResponse> listar() {
		return clientes.listar().stream().map(ClienteResponse::de).toList();
	}

	@GetMapping("/{id}")
	ClienteResponse buscar(@PathVariable UUID id) {
		return ClienteResponse.de(clientes.buscar(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ClienteResponse criar(@RequestBody NovoClienteRequest pedido) {
		return ClienteResponse
			.de(clientes.criar(new ClienteService.NovoCliente(pedido.codigo(), pedido.nome(), pedido.contatoNome(),
					pedido.contatoEmail(), pedido.limites() == null ? null : pedido.limites().modelo())));
	}

	@PutMapping("/{id}")
	ClienteResponse alterar(@PathVariable UUID id, @RequestBody AlteracaoClienteRequest pedido) {
		return ClienteResponse.de(clientes.alterar(id, new ClienteService.Alteracao(pedido.nome(), pedido.ativo(),
				pedido.limites() == null ? null : pedido.limites().modelo())));
	}

	/**
	 * Retoma o provisionamento que ficou pela metade (ex.: provedor de identidade fora do
	 * ar).
	 */
	@PostMapping("/{id}/provisionamento")
	ClienteResponse concluirProvisionamento(@PathVariable UUID id) {
		return ClienteResponse.de(clientes.concluirProvisionamento(id));
	}

	@PostMapping("/{id}/convite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reenviarConvite(@PathVariable UUID id) {
		clientes.reenviarConvite(id);
	}

	@PutMapping(path = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ClienteResponse definirLogo(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo)
			throws IOException {
		return ClienteResponse.de(clientes.definirLogo(id, arquivo.getBytes()));
	}

	@DeleteMapping("/{id}/logo")
	ClienteResponse removerLogo(@PathVariable UUID id) {
		return ClienteResponse.de(clientes.definirLogo(id, null));
	}

}
