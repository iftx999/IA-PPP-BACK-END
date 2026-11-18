package dev.aurora.campanhas.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.FaixaDoLote;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.Ignorada;
import dev.aurora.campanhas.model.LadoDaFaixa;
import dev.aurora.campanhas.model.OrigemOrdem;
import dev.aurora.campanhas.model.PreviaLote;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Programacao;
import dev.aurora.campanhas.model.ResultadoCriacao;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.service.EquipeService;
import dev.aurora.campanhas.service.OrdemServicoService;
import dev.aurora.campanhas.service.ProgramacaoService;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.servicos.ServicoDaOrdem;
import jakarta.validation.Valid;
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

/**
 * Programação das OS (J4, J5; ADR 0044): OS avulsas por imóvel, de trecho e lotes por
 * logradouro; lista geral; programar, cancelar e encerrar pelo gestor.
 */
@RestController
@RequestMapping("/api/v1/ordens")
@ExigePermissao("os:programar")
class OrdemController {

	/**
	 * @param alvo IMOVEL, TRECHO ou AMBOS
	 * @param prazoEmergencialHoras horas corridas; os demais em dias úteis (RN-21)
	 */
	record ServicoParaOrdemResponse(UUID id, int versao, String codigo, String nome, String grupoNome, String alvo,
			String unidadeMedida, boolean conferidoPelaCarga, int prazoEmergencialHoras, int prazoAltaDias,
			int prazoMediaDias, int prazoBaixaDias) {

		static ServicoParaOrdemResponse de(ServicoDaOrdem s) {
			return new ServicoParaOrdemResponse(s.id(), s.versao(), s.codigo(), s.nome(), s.grupoNome(), s.alvo(),
					s.unidadeMedida(), s.conferidoPelaCarga(), s.prazoEmergencialHoras(), s.prazoAltaDias(),
					s.prazoMediaDias(), s.prazoBaixaDias());
		}
	}

	record OpcoesResponse(List<ServicoParaOrdemResponse> servicos, List<MotivoDaOrdemResponse> motivos) {
	}

	record PaginaResponse(List<OrdemServicoResponse> itens, long total, int pagina, int tamanho) {
	}

	record ParaImoveisRequest(@NotNull UUID servicoId,
			@NotNull @Size(min = 1, max = 500) List<@NotNull @Size(min = 1, max = 40) String> matriculas,
			@NotNull Prioridade prioridade, @Nullable @Size(max = 500) String instrucoes) {
	}

	record ParaTrechosRequest(@NotNull UUID servicoId, @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> trechos,
			@NotNull Prioridade prioridade, @Nullable @Size(max = 500) String instrucoes) {
	}

	/**
	 * @param numeroInicial sem faixa (os dois nulos): o logradouro inteiro, inclusive os
	 * imóveis sem número
	 */
	record FaixaRequest(@NotNull UUID logradouroId, @Nullable @Min(0) Integer numeroInicial,
			@Nullable @Min(0) Integer numeroFinal, @NotNull LadoDaFaixa lado, @NotNull UUID servicoId) {

		FaixaDoLote faixa() {
			return new FaixaDoLote(logradouroId, numeroInicial, numeroFinal, lado);
		}
	}

	record LoteRequest(@NotNull @Valid FaixaRequest faixa, @NotNull Prioridade prioridade,
			@Nullable @Size(max = 500) String instrucoes) {
	}

	/**
	 * @param novas imóveis que viram OS (sem os que já têm OS aberta do serviço)
	 * @param limite máximo de OS por lote (RN-24)
	 */
	record PreviaLoteResponse(UUID logradouroId, String logradouroNome, UUID unidadeId, long imoveis, long comOsAberta,
			long novas, int limite) {

		static PreviaLoteResponse de(PreviaLote p) {
			return new PreviaLoteResponse(p.logradouroId(), p.logradouroNome(), p.unidadeId(), p.imoveis(),
					p.comOsAberta(), p.novas(), p.limite());
		}
	}

	/**
	 * @param alvo matrícula (inteira: quem cria a OS já a informou) ou código do trecho
	 */
	record IgnoradaResponse(String alvo, String motivo) {

		static IgnoradaResponse de(Ignorada i) {
			return new IgnoradaResponse(i.alvo(), i.motivo());
		}
	}

	/**
	 * @param ignoradas até as 200 primeiras
	 */
	record CriacaoResponse(@Nullable UUID loteId, int criadas, List<IgnoradaResponse> ignoradas, int totalIgnoradas) {

		static CriacaoResponse de(ResultadoCriacao r) {
			return new CriacaoResponse(r.loteId(), r.criadas(),
					r.ignoradas().stream().map(IgnoradaResponse::de).toList(), r.totalIgnoradas());
		}
	}

	record ProgramacaoRequest(@NotNull @Size(min = 1, max = 5000) List<@NotNull UUID> ordens, @NotNull UUID equipeId,
			@NotNull LocalDate data) {
	}

	/**
	 * @param foraDaArea não programadas: o alvo fica fora da unidade onde a equipe atua
	 * @param ignoradas não programadas: já em execução, encerradas ou canceladas
	 */
	record ProgramacaoResponse(int programadas, int foraDaArea, int ignoradas) {
	}

	/**
	 * Equipe ativa para programar (o cadastro fica em {@code /equipes}).
	 *
	 * @param ordensEmCampo OS com a equipe (programadas, em execução, paralisadas)
	 */
	record EquipeParaProgramarResponse(UUID id, String nome, UUID unidadeId, String unidadeNome, TipoEquipe tipo,
			long ordensEmCampo) {

		static EquipeParaProgramarResponse de(Equipe e) {
			return new EquipeParaProgramarResponse(e.id(), e.nome(), e.unidadeId(), e.unidadeNome(), e.tipo(),
					e.ordensEmCampo());
		}
	}

	private final ProgramacaoService programacao;

	private final OrdemServicoService ordens;

	private final EquipeService equipes;

	OrdemController(ProgramacaoService programacao, OrdemServicoService ordens, EquipeService equipes) {
		this.programacao = programacao;
		this.ordens = ordens;
		this.equipes = equipes;
	}

	/** Equipes ativas da área, para programar. */
	@GetMapping("/equipes")
	List<EquipeParaProgramarResponse> equipes() {
		return equipes.listar().stream().filter(Equipe::ativa).map(EquipeParaProgramarResponse::de).toList();
	}

	/** Serviços ativos (versão atual) e motivos, para os formulários. */
	@GetMapping("/opcoes")
	OpcoesResponse opcoes() {
		var o = programacao.opcoes();
		return new OpcoesResponse(o.servicos().stream().map(ServicoParaOrdemResponse::de).toList(),
				o.motivos().stream().map(MotivoDaOrdemResponse::de).toList());
	}

	/**
	 * @param busca número da OS ou matrícula exata
	 * @param unidade a unidade e as de baixo
	 */
	@GetMapping
	PaginaResponse listar(@RequestParam(required = false) @Nullable StatusOrdem status,
			@RequestParam(required = false) @Nullable UUID servico,
			@RequestParam(required = false) @Nullable UUID equipe,
			@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(required = false) @Nullable UUID campanha,
			@RequestParam(required = false) @Nullable UUID lote,
			@RequestParam(required = false) @Nullable OrigemOrdem origem,
			@RequestParam(required = false) @Nullable Prioridade prioridade,
			@RequestParam(required = false) @Nullable Conferencia conferencia,
			@RequestParam(required = false) @Nullable LocalDate data,
			@RequestParam(defaultValue = "false") boolean atrasadas,
			@RequestParam(required = false) @Nullable @Size(max = 40) String busca,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int pagina,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int tamanho) {
		var termo = busca == null || busca.isBlank() ? null : busca.strip();
		var p = programacao.listar(new FiltroOrdens(campanha, status, equipe, conferencia, prioridade, atrasadas,
				servico, unidade, lote, origem, data, termo), pagina, tamanho);
		return new PaginaResponse(p.itens().stream().map(OrdemServicoResponse::de).toList(), p.total(), p.pagina(),
				p.tamanho());
	}

	@GetMapping("/{id}")
	OrdemServicoResponse buscar(@PathVariable UUID id) {
		return OrdemServicoResponse.de(programacao.buscar(id));
	}

	/** J4: OS avulsas, uma por imóvel. */
	@PostMapping("/imoveis")
	@ResponseStatus(HttpStatus.CREATED)
	CriacaoResponse paraImoveis(@Valid @RequestBody ParaImoveisRequest pedido) {
		return CriacaoResponse.de(programacao.criarParaImoveis(pedido.servicoId(), pedido.matriculas(),
				pedido.prioridade(), pedido.instrucoes()));
	}

	/** J5: OS de rede, uma por trecho. */
	@PostMapping("/trechos")
	@ResponseStatus(HttpStatus.CREATED)
	CriacaoResponse paraTrechos(@Valid @RequestBody ParaTrechosRequest pedido) {
		return CriacaoResponse.de(programacao.criarParaTrechos(pedido.servicoId(), pedido.trechos(),
				pedido.prioridade(), pedido.instrucoes()));
	}

	/** J5: quantos imóveis entram no lote, sem criar nada. */
	@PostMapping("/lotes/previa")
	PreviaLoteResponse previaLote(@Valid @RequestBody FaixaRequest pedido) {
		return PreviaLoteResponse.de(programacao.previaLote(pedido.faixa(), pedido.servicoId()));
	}

	/** J5, RN-24: uma OS por imóvel do logradouro ou da faixa. */
	@PostMapping("/lotes")
	@ResponseStatus(HttpStatus.CREATED)
	CriacaoResponse lote(@Valid @RequestBody LoteRequest pedido) {
		return CriacaoResponse.de(programacao.criarLote(pedido.faixa().faixa(), pedido.faixa().servicoId(),
				pedido.prioridade(), pedido.instrucoes()));
	}

	/** Programa ou reprograma as OS para a equipe na data (RN-23). */
	@PostMapping("/programar")
	ProgramacaoResponse programar(@Valid @RequestBody ProgramacaoRequest pedido) {
		var r = programacao.programar(new Programacao(pedido.ordens(), pedido.equipeId(), pedido.data()));
		return new ProgramacaoResponse(r.enviadas(), r.foraDaArea(), r.ignoradas());
	}

	@PostMapping("/{id}/cancelar")
	OrdemServicoResponse cancelar(@PathVariable UUID id, @Valid @RequestBody JustificativaRequest pedido) {
		return OrdemServicoResponse.de(programacao.cancelar(id, pedido.justificativa()));
	}

	/** Encerramento informado pelo gestor (ex.: empreiteira sem acesso à Aurora). */
	@PutMapping("/{id}/resultado")
	OrdemServicoResponse encerrar(@PathVariable UUID id, @Valid @RequestBody ResultadoRequest pedido) {
		return OrdemServicoResponse.de(ordens.encerrarPeloGestor(id, pedido.registro()));
	}

}
