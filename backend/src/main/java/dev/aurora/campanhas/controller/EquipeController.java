package dev.aurora.campanhas.controller;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.model.DadosEquipe;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.ItemDoCatalogo;
import dev.aurora.campanhas.model.Membro;
import dev.aurora.campanhas.model.OperacaoDaEquipe;
import dev.aurora.campanhas.model.Pessoa;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.service.EquipeService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Equipes de campo (ADR 0036). */
@RestController
@RequestMapping("/api/v1/equipes")
@ExigePermissao("campanha:gerenciar")
class EquipeController {

	/**
	 * @param ordensEmCampo OS com a equipe esperando resultado
	 */
	record EquipeResponse(UUID id, UUID unidadeId, String unidadeNome, String nome, TipoEquipe tipo,
			@Nullable String empresa, boolean ativa, List<MembroResponse> membros, long ordensEmCampo,
			OperacaoResponse operacao) {

		static EquipeResponse de(Equipe e) {
			return new EquipeResponse(e.id(), e.unidadeId(), e.unidadeNome(), e.nome(), e.tipo(), e.empresa(),
					e.ativa(), e.membros().stream().map(MembroResponse::de).toList(), e.ordensEmCampo(),
					OperacaoResponse.de(e.operacao()));
		}
	}

	record BaseDaEquipeDto(@DecimalMin("-90") @DecimalMax("90") double latitude,
			@DecimalMin("-180") @DecimalMax("180") double longitude) {
	}

	/**
	 * @param base ponto de partida da prévia da rota
	 * @param jornadaInicio hora local, HH:mm
	 * @param pausaMin almoço e paradas, descontados da jornada
	 */
	record OperacaoResponse(@Nullable BaseDaEquipeDto base, LocalTime jornadaInicio, LocalTime jornadaFim, int pausaMin,
			@Nullable UUID tipoVeiculoId, List<UUID> habilidades) {

		static OperacaoResponse de(OperacaoDaEquipe o) {
			var base = o.base();
			return new OperacaoResponse(base == null ? null : new BaseDaEquipeDto(base.latitude(), base.longitude()),
					o.jornadaInicio(), o.jornadaFim(), o.pausaMin(), o.tipoVeiculoId(), o.habilidades());
		}
	}

	record OperacaoRequest(@Nullable @Valid BaseDaEquipeDto base, @NotNull LocalTime jornadaInicio,
			@NotNull LocalTime jornadaFim, @Min(0) @Max(240) int pausaMin, @Nullable UUID tipoVeiculoId,
			@NotNull @Size(max = 50) List<@NotNull UUID> habilidades) {

		OperacaoDaEquipe dados() {
			return new OperacaoDaEquipe(base == null ? null : new Posicao(base.latitude(), base.longitude(), null),
					jornadaInicio, jornadaFim, pausaMin, tipoVeiculoId, habilidades);
		}
	}

	record ItemDoCatalogoResponse(UUID id, String codigo, String nome, boolean ativo, @Nullable String perfilRota) {

		static ItemDoCatalogoResponse de(ItemDoCatalogo i) {
			return new ItemDoCatalogoResponse(i.id(), i.codigo(), i.nome(), i.ativo(), i.perfilRota());
		}
	}

	record OpcoesDaEquipeResponse(List<ItemDoCatalogoResponse> veiculos, List<ItemDoCatalogoResponse> habilidades) {
	}

	record MembroResponse(UUID usuarioId, String nome, String email, boolean ativo) {

		static MembroResponse de(Membro m) {
			return new MembroResponse(m.usuarioId(), m.nome(), m.email(), m.ativo());
		}
	}

	record PessoaResponse(UUID id, String nome, String email) {

		static PessoaResponse de(Pessoa p) {
			return new PessoaResponse(p.id(), p.nome(), p.email());
		}
	}

	/**
	 * @param empresa obrigatória na equipe terceirizada
	 * @param operacao sem ela, jornada padrão (07:30–16:30, 60 min de pausa) sem base,
	 * veículo nem habilidades
	 */
	record EquipeRequest(@NotNull UUID unidadeId, @NotNull @Size(min = 2, max = 80) String nome,
			@NotNull TipoEquipe tipo, @Nullable @Size(max = 120) String empresa, boolean ativa,
			@NotNull @Size(max = 50) List<@NotNull UUID> membros, @Nullable @Valid OperacaoRequest operacao) {

		DadosEquipe dados() {
			return new DadosEquipe(unidadeId, nome, tipo, empresa, ativa, membros,
					operacao == null ? OperacaoDaEquipe.padrao() : operacao.dados());
		}
	}

	private final EquipeService equipes;

	EquipeController(EquipeService equipes) {
		this.equipes = equipes;
	}

	@GetMapping
	List<EquipeResponse> listar() {
		return equipes.listar().stream().map(EquipeResponse::de).toList();
	}

	/** Tipos de veículo e habilidades do catálogo, para o cadastro. */
	@GetMapping("/opcoes")
	OpcoesDaEquipeResponse opcoes() {
		var opcoes = equipes.opcoes();
		return new OpcoesDaEquipeResponse(opcoes.veiculos().stream().map(ItemDoCatalogoResponse::de).toList(),
				opcoes.habilidades().stream().map(ItemDoCatalogoResponse::de).toList());
	}

	/** Pessoas com acesso ativo, pelo nome ou e-mail (a partir de 2 letras). */
	@GetMapping("/pessoas")
	List<PessoaResponse> pessoas(@RequestParam @Size(max = 80) String busca) {
		return equipes.buscarPessoas(busca).stream().map(PessoaResponse::de).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	EquipeResponse cadastrar(@Valid @RequestBody EquipeRequest pedido) {
		return EquipeResponse.de(equipes.cadastrar(pedido.dados()));
	}

	@PutMapping("/{id}")
	EquipeResponse alterar(@PathVariable UUID id, @Valid @RequestBody EquipeRequest pedido) {
		return EquipeResponse.de(equipes.alterar(id, pedido.dados()));
	}

}
