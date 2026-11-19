package dev.aurora.campanhas.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoDeCampo;
import dev.aurora.campanhas.model.Anexo;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.Conflito;
import dev.aurora.campanhas.model.Equipe;
import dev.aurora.campanhas.model.EventoDaOrdem;
import dev.aurora.campanhas.model.FaixaDoLote;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.Ignorada;
import dev.aurora.campanhas.model.LadoDaFaixa;
import dev.aurora.campanhas.model.OrigemOrdem;
import dev.aurora.campanhas.model.PreviaLote;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Programacao;
import dev.aurora.campanhas.model.ResultadoCriacao;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoConflito;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.service.AcompanhamentoService;
import dev.aurora.campanhas.service.AnexoService;
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
import tools.jackson.databind.JsonNode;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	 * @param incompativeis não programadas: o serviço exige habilidade ou veículo que a
	 * equipe não tem (RN-23)
	 * @param ignoradas não programadas: já em execução, encerradas ou canceladas
	 */
	record ProgramacaoResponse(int programadas, int foraDaArea, int incompativeis, int ignoradas) {
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

	private final AcompanhamentoService acompanhamento;

	private final AnexoService anexos;

	OrdemController(ProgramacaoService programacao, OrdemServicoService ordens, EquipeService equipes,
			AcompanhamentoService acompanhamento, AnexoService anexos) {
		this.programacao = programacao;
		this.ordens = ordens;
		this.equipes = equipes;
		this.acompanhamento = acompanhamento;
		this.anexos = anexos;
	}

	// ----- Execução no campo (J9, J11; ADR 0045) -----

	/**
	 * Evento do app no histórico da OS.
	 *
	 * @param situacao APLICADO, CONFLITO, SUPERADO, AGUARDANDO ou REJEITADO
	 * @param avisos RELOGIO, FORA_DO_LOCAL, SEM_GPS, SEM_JORNADA, EXECUCAO_SIMULTANEA,
	 * APARELHO_REVOGADO
	 */
	record EventoDaOrdemResponse(UUID id, int sequencia, AcaoDeCampo tipo, String situacao, @Nullable String mensagem,
			@Nullable ResultadoOrdem resultado, @Nullable String motivoNome, @Nullable String observacao,
			@Nullable BigDecimal quantidade, @Nullable JsonNode valores, Instant ocorridoEm, Instant recebidoEm,
			@Nullable Double latitude, @Nullable Double longitude, @Nullable BigDecimal precisaoM,
			@Nullable BigDecimal distanciaM, List<String> avisos, String equipeNome, String usuarioNome) {

		static EventoDaOrdemResponse de(EventoDaOrdem e) {
			return new EventoDaOrdemResponse(e.id(), e.sequencia(), e.tipo(), e.situacao().name(), e.mensagem(),
					e.resultado(), e.motivoNome(), e.observacao(), e.quantidade(), e.valores(), e.ocorridoEm(),
					e.recebidoEm(), e.latitude(), e.longitude(), e.precisaoM(), e.distanciaM(), e.avisos(),
					e.equipeNome(), e.usuarioNome());
		}
	}

	/**
	 * @param situacao RECEBENDO, DISPONIVEL ou RECUSADO
	 */
	record AnexoDaOrdemResponse(UUID id, @Nullable String parametro, String tipoMidia, int tamanho, int recebido,
			String situacao, @Nullable String recusa, Instant criadoEm) {

		static AnexoDaOrdemResponse de(Anexo a) {
			return new AnexoDaOrdemResponse(a.id(), a.parametro(), a.tipoMidia(), a.tamanho(), a.recebido(),
					a.situacao(), a.recusa(), a.criadoEm());
		}
	}

	record ConflitoResponse(UUID id, UUID osId, long osNumero, String servicoNome, StatusOrdem osStatus,
			@Nullable String equipeNome, TipoConflito tipo, String descricao, AcaoDeCampo acao, String agente,
			Instant ocorridoEm, Instant criadoEm, @Nullable String decisao, @Nullable String decididoPor,
			@Nullable Instant decididoEm) {

		static ConflitoResponse de(Conflito c) {
			return new ConflitoResponse(c.id(), c.osId(), c.osNumero(), c.servicoNome(), c.osStatus(), c.equipeNome(),
					c.tipo(), c.descricao(), c.acao(), c.agente(), c.ocorridoEm(), c.criadoEm(), c.decisao(),
					c.decididoPor(), c.decididoEm());
		}
	}

	/** Parâmetro da versão do serviço da OS, para mostrar os valores com o rótulo. */
	record ParametroDaExecucaoResponse(String chave, String rotulo, String tipo, @Nullable String unidade) {
	}

	record ExecucaoDaOrdemResponse(List<EventoDaOrdemResponse> eventos, List<AnexoDaOrdemResponse> anexos,
			List<ConflitoResponse> conflitos, List<ParametroDaExecucaoResponse> parametros) {
	}

	record PaginaConflitosResponse(List<ConflitoResponse> itens, long total, long abertos, int pagina, int tamanho) {
	}

	record DecisaoRequest(@NotNull @Size(min = 3, max = 500) String decisao) {
	}

	/** Eventos do app, fotos, conflitos e parâmetros da versão do serviço da OS. */
	@GetMapping("/{id}/execucao")
	ExecucaoDaOrdemResponse execucao(@PathVariable UUID id) {
		var e = acompanhamento.execucao(id);
		return new ExecucaoDaOrdemResponse(e.eventos().stream().map(EventoDaOrdemResponse::de).toList(),
				e.anexos().stream().map(AnexoDaOrdemResponse::de).toList(),
				e.conflitos().stream().map(ConflitoResponse::de).toList(),
				e.parametros()
					.stream()
					.map(p -> new ParametroDaExecucaoResponse(p.chave(), p.rotulo(), p.tipo(), p.unidade()))
					.toList());
	}

	/** Foto da OS (fica na trilha de auditoria). */
	@GetMapping("/{id}/anexos/{anexo}")
	ResponseEntity<byte[]> foto(@PathVariable UUID id, @PathVariable UUID anexo) {
		var conteudo = anexos.conteudo(id, anexo);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(anexos.tipoMidia(anexo)))
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(conteudo);
	}

	/** J11: conflitos de campo; abertos primeiro. */
	@GetMapping("/conflitos")
	PaginaConflitosResponse conflitos(@RequestParam(defaultValue = "true") boolean abertos,
			@RequestParam(defaultValue = "0") @Min(0) int pagina) {
		var p = acompanhamento.conflitos(abertos, pagina);
		return new PaginaConflitosResponse(p.itens().stream().map(ConflitoResponse::de).toList(), p.total(),
				p.abertos(), p.pagina(), p.tamanho());
	}

	@PostMapping("/conflitos/{id}/decisao")
	ConflitoResponse decidir(@PathVariable UUID id, @Valid @RequestBody DecisaoRequest pedido) {
		return ConflitoResponse.de(acompanhamento.decidir(id, pedido.decisao()));
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
		return new ProgramacaoResponse(r.enviadas(), r.foraDaArea(), r.incompativeis(), r.ignoradas());
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
