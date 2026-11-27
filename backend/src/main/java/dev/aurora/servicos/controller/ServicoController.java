package dev.aurora.servicos.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.servicos.FaseComFoto;
import dev.aurora.servicos.model.AlvoServico;
import dev.aurora.servicos.model.Condicao;
import dev.aurora.servicos.model.DadosServico;
import dev.aurora.servicos.model.DefinicaoServico;
import dev.aurora.servicos.model.Parametro;
import dev.aurora.servicos.model.Prazos;
import dev.aurora.servicos.model.Servico;
import dev.aurora.servicos.model.TipoParametro;
import dev.aurora.servicos.model.UnidadeMedida;
import dev.aurora.servicos.model.VersaoServico;
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

/** Serviços do catálogo e as versões de cada um (J1, J2, RN-20). */
@RestController
@RequestMapping("/api/v1/catalogo/servicos")
@ExigePermissao("servico:gerenciar")
class ServicoController {

	record CondicaoDto(@NotNull @Size(max = 40) String chave, @NotNull @Size(max = 60) String valor) {

		static @Nullable CondicaoDto de(@Nullable Condicao c) {
			return c == null ? null : new CondicaoDto(c.chave(), c.valor());
		}

		Condicao modelo() {
			return new Condicao(chave, valor);
		}
	}

	record ParametroDto(@NotNull @Size(max = 40) String chave, @NotNull @Size(max = 80) String rotulo,
			@Nullable @Size(max = 200) String ajuda, @NotNull TipoParametro tipo, boolean obrigatorio,
			@Nullable BigDecimal minimo, @Nullable BigDecimal maximo, @Nullable @Size(max = 10) String unidade,
			@Nullable @Size(max = 30) List<@NotNull @Size(max = 60) String> opcoes,
			@Nullable @Valid CondicaoDto condicao) {

		static ParametroDto de(Parametro p) {
			return new ParametroDto(p.chave(), p.rotulo(), p.ajuda(), p.tipo(), p.obrigatorio(), p.minimo(), p.maximo(),
					p.unidade(), p.opcoes(), CondicaoDto.de(p.condicao()));
		}

		Parametro modelo() {
			return new Parametro(chave, rotulo, ajuda, tipo, obrigatorio, minimo, maximo, unidade,
					opcoes == null ? List.of() : opcoes, condicao == null ? null : condicao.modelo());
		}
	}

	record PrazosDto(int emergencialHoras, int altaDias, int mediaDias, int baixaDias) {

		static PrazosDto de(Prazos p) {
			return new PrazosDto(p.emergencialHoras(), p.altaDias(), p.mediaDias(), p.baixaDias());
		}

		Prazos modelo() {
			return new Prazos(emergencialHoras, altaDias, mediaDias, baixaDias);
		}
	}

	/** O que é versionado: mudar qualquer campo daqui cria nova versão. */
	record DefinicaoDto(@NotNull @Size(max = 120) String nome, @Nullable @Size(max = 500) String descricao,
			@NotNull UnidadeMedida unidadeMedida, @NotNull AlvoServico alvo, @NotNull BigDecimal tempoPadraoMin,
			boolean tempoPorUnidade, @NotNull @Valid PrazosDto prazos, @Nullable UUID tipoVeiculoId,
			@Nullable UUID habilidadeId, boolean conferidoPelaCarga, @Nullable List<@NotNull FaseComFoto> fasesComFoto,
			@NotNull @Size(max = DefinicaoServico.MAXIMO_DE_PARAMETROS) List<@NotNull @Valid ParametroDto> parametros) {

		static DefinicaoDto de(DefinicaoServico d) {
			return new DefinicaoDto(d.nome(), d.descricao(), d.unidadeMedida(), d.alvo(), d.tempoPadraoMin(),
					d.tempoPorUnidade(), PrazosDto.de(d.prazos()), d.tipoVeiculoId(), d.habilidadeId(),
					d.conferidoPelaCarga(), d.fasesComFoto(), d.parametros().stream().map(ParametroDto::de).toList());
		}

		DefinicaoServico modelo() {
			return new DefinicaoServico(nome, descricao, unidadeMedida, alvo, tempoPadraoMin, tempoPorUnidade,
					prazos.modelo(), tipoVeiculoId, habilidadeId, conferidoPelaCarga,
					FaseComFoto.normalizadas(fasesComFoto), parametros.stream().map(ParametroDto::modelo).toList());
		}
	}

	record ServicoResponse(UUID id, UUID grupoId, String codigo, boolean ativo, int versao, Instant atualizadoEm,
			DefinicaoDto definicao) {

		static ServicoResponse de(Servico s) {
			return new ServicoResponse(s.id(), s.grupoId(), s.codigo(), s.ativo(), s.versao(), s.atualizadoEm(),
					DefinicaoDto.de(s.definicao()));
		}
	}

	record VersaoResponse(int versao, Instant criadaEm, @Nullable String criadaPor, DefinicaoDto definicao) {

		static VersaoResponse de(VersaoServico v) {
			return new VersaoResponse(v.versao(), v.criadaEm(), v.criadaPor(), DefinicaoDto.de(v.definicao()));
		}
	}

	/**
	 * @param codigo só no cadastro; na alteração é ignorado (o código não muda)
	 */
	record ServicoRequest(@NotNull UUID grupoId, @Nullable @Size(max = 60) String codigo, boolean ativo,
			@NotNull @Valid DefinicaoDto definicao) {

		DadosServico dados() {
			return new DadosServico(grupoId, codigo == null ? definicao.nome() : codigo, ativo, definicao.modelo());
		}
	}

	private final CatalogoService catalogo;

	ServicoController(CatalogoService catalogo) {
		this.catalogo = catalogo;
	}

	/** Sem código, ele vem do nome ("Limpeza de PV" → LIMPEZA_DE_PV). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ServicoResponse cadastrar(@Valid @RequestBody ServicoRequest pedido) {
		return ServicoResponse.de(catalogo.cadastrarServico(pedido.dados()));
	}

	@PutMapping("/{id}")
	ServicoResponse alterar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest pedido) {
		return ServicoResponse.de(catalogo.alterarServico(id, pedido.dados()));
	}

	@GetMapping("/{id}/versoes")
	List<VersaoResponse> versoes(@PathVariable UUID id) {
		return catalogo.versoes(id).stream().map(VersaoResponse::de).toList();
	}

	static ServicoResponse resposta(Servico s) {
		return ServicoResponse.de(s);
	}

}
