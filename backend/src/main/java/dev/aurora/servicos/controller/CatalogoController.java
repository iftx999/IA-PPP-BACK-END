package dev.aurora.servicos.controller;

import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.servicos.controller.ServicoController.ServicoResponse;
import dev.aurora.servicos.model.DadosGrupo;
import dev.aurora.servicos.model.DadosHabilidade;
import dev.aurora.servicos.model.DadosMotivo;
import dev.aurora.servicos.model.DadosTipoVeiculo;
import dev.aurora.servicos.model.Grupo;
import dev.aurora.servicos.model.Habilidade;
import dev.aurora.servicos.model.Motivo;
import dev.aurora.servicos.model.PerfilRota;
import dev.aurora.servicos.model.TipoMotivo;
import dev.aurora.servicos.model.TipoVeiculo;
import dev.aurora.servicos.service.CatalogoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo completo e os itens de apoio: grupos, tipos de veículo, habilidades e motivos
 * (J1, J2).
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@ExigePermissao("servico:gerenciar")
class CatalogoController {

	record GrupoResponse(UUID id, String codigo, String nome, boolean ativo, long servicos) {

		static GrupoResponse de(Grupo g) {
			return new GrupoResponse(g.id(), g.codigo(), g.nome(), g.ativo(), g.servicos());
		}
	}

	record VeiculoResponse(UUID id, String codigo, String nome, PerfilRota perfilRota, boolean ativo) {

		static VeiculoResponse de(TipoVeiculo v) {
			return new VeiculoResponse(v.id(), v.codigo(), v.nome(), v.perfilRota(), v.ativo());
		}
	}

	record HabilidadeResponse(UUID id, String codigo, String nome, boolean ativo) {

		static HabilidadeResponse de(Habilidade h) {
			return new HabilidadeResponse(h.id(), h.codigo(), h.nome(), h.ativo());
		}
	}

	record MotivoDoCatalogoResponse(UUID id, TipoMotivo tipo, String codigo, String nome, boolean exigeObservacao,
			@Nullable UUID servicoId, boolean ativo) {

		static MotivoDoCatalogoResponse de(Motivo m) {
			return new MotivoDoCatalogoResponse(m.id(), m.tipo(), m.codigo(), m.nome(), m.exigeObservacao(),
					m.servicoId(), m.ativo());
		}
	}

	record CatalogoResponse(List<GrupoResponse> grupos, List<ServicoResponse> servicos,
			List<MotivoDoCatalogoResponse> motivos, List<VeiculoResponse> veiculos,
			List<HabilidadeResponse> habilidades) {
	}

	/** Grupo e habilidade: sem código, ele vem do nome. */
	record ItemDoCatalogoRequest(@Nullable @Size(max = 60) String codigo, @NotNull @Size(max = 80) String nome,
			boolean ativo) {

		String codigoOuNome() {
			return codigo == null || codigo.isBlank() ? nome : codigo;
		}
	}

	record VeiculoRequest(@Nullable @Size(max = 60) String codigo, @NotNull @Size(max = 80) String nome,
			@NotNull PerfilRota perfilRota, boolean ativo) {

		DadosTipoVeiculo dados() {
			return new DadosTipoVeiculo(codigo == null || codigo.isBlank() ? nome : codigo, nome, perfilRota, ativo);
		}
	}

	/**
	 * @param servicoId nulo: vale para todos os serviços
	 */
	record MotivoRequest(@NotNull TipoMotivo tipo, @Nullable @Size(max = 60) String codigo,
			@NotNull @Size(max = 80) String nome, boolean exigeObservacao, @Nullable UUID servicoId, boolean ativo) {

		DadosMotivo dados() {
			return new DadosMotivo(tipo, codigo == null || codigo.isBlank() ? nome : codigo, nome, exigeObservacao,
					servicoId, ativo);
		}
	}

	private final CatalogoService catalogo;

	CatalogoController(CatalogoService catalogo) {
		this.catalogo = catalogo;
	}

	@GetMapping
	CatalogoResponse completo() {
		var c = catalogo.completo();
		return new CatalogoResponse(c.grupos().stream().map(GrupoResponse::de).toList(),
				c.servicos().stream().map(ServicoController::resposta).toList(),
				c.motivos().stream().map(MotivoDoCatalogoResponse::de).toList(),
				c.veiculos().stream().map(VeiculoResponse::de).toList(),
				c.habilidades().stream().map(HabilidadeResponse::de).toList());
	}

	@PostMapping("/grupos")
	@ResponseStatus(HttpStatus.CREATED)
	GrupoResponse cadastrarGrupo(@Valid @RequestBody ItemDoCatalogoRequest pedido) {
		return GrupoResponse.de(catalogo.cadastrarGrupo(grupo(pedido)));
	}

	@PutMapping("/grupos/{id}")
	GrupoResponse alterarGrupo(@PathVariable UUID id, @Valid @RequestBody ItemDoCatalogoRequest pedido) {
		return GrupoResponse.de(catalogo.alterarGrupo(id, grupo(pedido)));
	}

	@PostMapping("/veiculos")
	@ResponseStatus(HttpStatus.CREATED)
	VeiculoResponse cadastrarVeiculo(@Valid @RequestBody VeiculoRequest pedido) {
		return VeiculoResponse.de(catalogo.cadastrarVeiculo(pedido.dados()));
	}

	@PutMapping("/veiculos/{id}")
	VeiculoResponse alterarVeiculo(@PathVariable UUID id, @Valid @RequestBody VeiculoRequest pedido) {
		return VeiculoResponse.de(catalogo.alterarVeiculo(id, pedido.dados()));
	}

	@PostMapping("/habilidades")
	@ResponseStatus(HttpStatus.CREATED)
	HabilidadeResponse cadastrarHabilidade(@Valid @RequestBody ItemDoCatalogoRequest pedido) {
		return HabilidadeResponse.de(catalogo.cadastrarHabilidade(habilidade(pedido)));
	}

	@PutMapping("/habilidades/{id}")
	HabilidadeResponse alterarHabilidade(@PathVariable UUID id, @Valid @RequestBody ItemDoCatalogoRequest pedido) {
		return HabilidadeResponse.de(catalogo.alterarHabilidade(id, habilidade(pedido)));
	}

	@PostMapping("/motivos")
	@ResponseStatus(HttpStatus.CREATED)
	MotivoDoCatalogoResponse cadastrarMotivo(@Valid @RequestBody MotivoRequest pedido) {
		return MotivoDoCatalogoResponse.de(catalogo.cadastrarMotivo(pedido.dados()));
	}

	@PutMapping("/motivos/{id}")
	MotivoDoCatalogoResponse alterarMotivo(@PathVariable UUID id, @Valid @RequestBody MotivoRequest pedido) {
		return MotivoDoCatalogoResponse.de(catalogo.alterarMotivo(id, pedido.dados()));
	}

	private static DadosGrupo grupo(ItemDoCatalogoRequest pedido) {
		return new DadosGrupo(pedido.codigoOuNome(), pedido.nome(), pedido.ativo());
	}

	private static DadosHabilidade habilidade(ItemDoCatalogoRequest pedido) {
		return new DadosHabilidade(pedido.codigoOuNome(), pedido.nome(), pedido.ativo());
	}

}
