package dev.aurora.campanhas.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.campanhas.model.AcaoCampanha;
import dev.aurora.campanhas.model.Area;
import dev.aurora.campanhas.model.Campanha;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.ContagemMotivo;
import dev.aurora.campanhas.model.Distribuicao;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.Funil;
import dev.aurora.campanhas.model.ImpactoNaMeta;
import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.NovaCampanha;
import dev.aurora.campanhas.model.OrigemCampanha;
import dev.aurora.campanhas.model.Previa;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Produtividade;
import dev.aurora.campanhas.model.Recorte;
import dev.aurora.campanhas.model.StatusCampanha;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoEquipe;
import dev.aurora.campanhas.service.CampanhaService;
import dev.aurora.campanhas.service.OrdemServicoService;
import dev.aurora.contexto.ExigePermissao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

/** Campanhas de ligação, OS e painel (I2–I4, ADR 0036). */
@RestController
@RequestMapping("/api/v1/campanhas")
@ExigePermissao("campanha:gerenciar")
class CampanhaController {

	/** Retângulo visível do mapa, em graus (lon/lat), no entorno do Brasil. */
	record AreaDaCampanha(@DecimalMin("-75") @DecimalMax("-28") double oeste,
			@DecimalMin("-35") @DecimalMax("7") double sul, @DecimalMin("-75") @DecimalMax("-28") double leste,
			@DecimalMin("-35") @DecimalMax("7") double norte) {

		@AssertTrue(message = "a área precisa ter oeste < leste e sul < norte")
		boolean isOrdenada() {
			return oeste < leste && sul < norte;
		}

		static @Nullable AreaDaCampanha de(@Nullable Area a) {
			return a == null ? null : new AreaDaCampanha(a.oeste(), a.sul(), a.leste(), a.norte());
		}
	}

	/**
	 * @param situacao FACTIVEL ou CORTADO; omitida = os dois
	 * @param motivoCorte INADIMPLENCIA ou A_PEDIDO (só cortados)
	 * @param area obrigatória (e só aceita) na origem área do mapa
	 */
	record RecorteDaCampanha(@NotNull OrigemCampanha origem, @NotNull UUID unidadeId,
			@Nullable @Pattern(regexp = "FACTIVEL|CORTADO") String situacao,
			@Nullable @Pattern(regexp = "INADIMPLENCIA|A_PEDIDO") String motivoCorte,
			@Nullable @Valid AreaDaCampanha area) {

		Recorte recorte() {
			return new Recorte(origem, unidadeId, situacao, motivoCorte,
					area == null ? null : new Area(area.oeste(), area.sul(), area.leste(), area.norte()));
		}

		static RecorteDaCampanha de(Recorte r) {
			return new RecorteDaCampanha(r.origem(), r.unidadeId(), r.situacao(), r.motivoCorte(),
					AreaDaCampanha.de(r.area()));
		}
	}

	record CampanhaRequest(@NotNull @Size(min = 3, max = 120) String nome, @NotNull @Valid RecorteDaCampanha recorte,
			@NotNull AcaoCampanha acao, @NotNull LocalDate prazo) {
	}

	/**
	 * @param faltamParaMeta ligações até a meta; nulo sem meta
	 * @param adesaoSeTodasLigarem adesão se as OS (da prévia, ou as de ligação em aberto
	 * no painel) virarem ligação
	 */
	record ImpactoNaMetaResponse(UUID unidadeId, String unidadeNome, @Nullable YearMonth competencia, long ligados,
			long comRede, @Nullable BigDecimal adesao, @Nullable BigDecimal adesaoSeTodasLigarem,
			@Nullable BigDecimal meta, @Nullable Long faltamParaMeta) {

		static ImpactoNaMetaResponse de(ImpactoNaMeta i) {
			return new ImpactoNaMetaResponse(i.unidadeId(), i.unidadeNome(), i.competencia(), i.ligados(), i.comRede(),
					i.adesao(), i.adesaoSeTodasLigarem(), i.meta(), i.faltamParaMeta());
		}
	}

	/**
	 * @param imoveis imóveis que viram OS (sem os que já têm OS aberta)
	 * @param limite máximo de OS por campanha
	 */
	record PreviaResponse(YearMonth competencia, long imoveis, long factiveis, long cortados, long comOsAberta,
			long alta, long media, long baixa, long limite, ImpactoNaMetaResponse impacto) {

		static PreviaResponse de(Previa p) {
			return new PreviaResponse(p.competencia(), p.imoveis(), p.factiveis(), p.cortados(), p.comOsAberta(),
					p.alta(), p.media(), p.baixa(), p.limite(), ImpactoNaMetaResponse.de(p.impacto()));
		}
	}

	record FunilResponse(long total, long planejadas, long emCampo, long executadas, long confirmadas, long divergentes,
			long pendentesDeConferencia, long recusadas, long inviaveis, long reagendadas, long canceladas,
			long semEquipe, long atrasadas) {

		static FunilResponse de(Funil f) {
			return new FunilResponse(f.total(), f.planejadas(), f.emCampo(), f.executadas(), f.confirmadas(),
					f.divergentes(), f.pendentesDeConferencia(), f.recusadas(), f.inviaveis(), f.reagendadas(),
					f.canceladas(), f.semEquipe(), f.atrasadas());
		}
	}

	record CampanhaResponse(UUID id, UUID unidadeId, String unidadeNome, String nome, RecorteDaCampanha recorte,
			AcaoCampanha acao, YearMonth competencia, LocalDate prazo, StatusCampanha status, long ligadosNaCriacao,
			long comRedeNaCriacao, String criadaPor, Instant criadaEm, @Nullable Instant finalizadaEm,
			FunilResponse funil) {

		static CampanhaResponse de(Campanha c) {
			return new CampanhaResponse(c.id(), c.unidadeId(), c.unidadeNome(), c.nome(),
					RecorteDaCampanha.de(c.recorte()), c.acao(), c.competencia(), c.prazo(), c.status(),
					c.ligadosNaCriacao(), c.comRedeNaCriacao(), c.criadaPor(), c.criadaEm(), c.finalizadaEm(),
					FunilResponse.de(c.funil()));
		}
	}

	record ProdutividadeResponse(UUID equipeId, String equipeNome, TipoEquipe tipo, long emCampo, long executadas,
			long confirmadas, long recusadas, long inviaveis, long atrasadas) {

		static ProdutividadeResponse de(Produtividade p) {
			return new ProdutividadeResponse(p.equipeId(), p.equipeNome(), p.tipo(), p.emCampo(), p.executadas(),
					p.confirmadas(), p.recusadas(), p.inviaveis(), p.atrasadas());
		}
	}

	record MotivoResponse(StatusOrdem status, MotivoResultado motivo, long quantidade) {

		static MotivoResponse de(ContagemMotivo m) {
			return new MotivoResponse(m.status(), m.motivo(), m.quantidade());
		}
	}

	/**
	 * @param agora adesão atual da unidade (efeito real), comparável com a da criação
	 */
	record PainelCampanhaResponse(CampanhaResponse campanha, List<ProdutividadeResponse> equipes,
			List<MotivoResponse> motivos, ImpactoNaMetaResponse agora) {
	}

	record PaginaOrdensResponse(List<OrdemServicoResponse> itens, long total, int pagina, int tamanho) {
	}

	/**
	 * @param ordens OS escolhidas; vazia = as planejadas na ordem de prioridade
	 * @param prioridade só com a lista vazia: envia só as dessa prioridade
	 * @param quantidade só com a lista vazia: até quantas enviar
	 */
	record DistribuicaoRequest(@NotNull UUID equipeId, @NotNull @Size(max = 5000) List<@NotNull UUID> ordens,
			@Nullable Prioridade prioridade, @Nullable @Min(1) @Max(5000) Integer quantidade) {
	}

	/**
	 * @param foraDaArea não enviadas: o imóvel fica fora da unidade onde a equipe atua
	 */
	record DistribuicaoResponse(int enviadas, int foraDaArea) {
	}

	private final CampanhaService campanhas;

	private final OrdemServicoService ordens;

	CampanhaController(CampanhaService campanhas, OrdemServicoService ordens) {
		this.campanhas = campanhas;
		this.ordens = ordens;
	}

	@GetMapping
	List<CampanhaResponse> listar(@RequestParam(required = false) @Nullable StatusCampanha status) {
		return campanhas.listar(status).stream().map(CampanhaResponse::de).toList();
	}

	/** Quantos imóveis entram e o impacto na meta, sem criar nada. */
	@PostMapping("/previa")
	PreviaResponse previa(@Valid @RequestBody RecorteDaCampanha recorte) {
		return PreviaResponse.de(campanhas.previa(recorte.recorte()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CampanhaResponse criar(@Valid @RequestBody CampanhaRequest pedido) {
		return CampanhaResponse.de(campanhas
			.criar(new NovaCampanha(pedido.nome(), pedido.recorte().recorte(), pedido.acao(), pedido.prazo())));
	}

	@GetMapping("/{id}")
	PainelCampanhaResponse painel(@PathVariable UUID id) {
		var p = campanhas.painel(id);
		return new PainelCampanhaResponse(CampanhaResponse.de(p.campanha()),
				p.equipes().stream().map(ProdutividadeResponse::de).toList(),
				p.motivos().stream().map(MotivoResponse::de).toList(), ImpactoNaMetaResponse.de(p.agora()));
	}

	@GetMapping("/{id}/ordens")
	PaginaOrdensResponse ordens(@PathVariable UUID id, @RequestParam(required = false) @Nullable StatusOrdem status,
			@RequestParam(required = false) @Nullable UUID equipe,
			@RequestParam(required = false) @Nullable Conferencia conferencia,
			@RequestParam(required = false) @Nullable Prioridade prioridade,
			@RequestParam(defaultValue = "false") boolean atrasadas,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int pagina,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int tamanho) {
		var p = campanhas.ordens(id, new FiltroOrdens(status, equipe, conferencia, prioridade, atrasadas), pagina,
				tamanho);
		return new PaginaOrdensResponse(p.itens().stream().map(OrdemServicoResponse::de).toList(), p.total(),
				p.pagina(), p.tamanho());
	}

	@PostMapping("/{id}/distribuir")
	DistribuicaoResponse distribuir(@PathVariable UUID id, @Valid @RequestBody DistribuicaoRequest pedido) {
		var r = campanhas.distribuir(id,
				new Distribuicao(pedido.equipeId(), pedido.ordens(), pedido.prioridade(), pedido.quantidade()));
		return new DistribuicaoResponse(r.enviadas(), r.foraDaArea());
	}

	/** Resultado informado pelo gestor (ex.: empreiteira sem acesso à Aurora). */
	@PutMapping("/ordens/{ordem}/resultado")
	OrdemServicoResponse registrar(@PathVariable UUID ordem, @Valid @RequestBody ResultadoRequest pedido) {
		return OrdemServicoResponse.de(ordens.registrarPeloGestor(ordem, pedido.registro()));
	}

	/** Conclui a campanha; as OS ainda em aberto são canceladas. */
	@PostMapping("/{id}/encerrar")
	CampanhaResponse encerrar(@PathVariable UUID id) {
		return CampanhaResponse.de(campanhas.encerrar(id));
	}

	/** Desiste da campanha; as OS ainda em aberto são canceladas. */
	@PostMapping("/{id}/cancelar")
	CampanhaResponse cancelar(@PathVariable UUID id) {
		return CampanhaResponse.de(campanhas.cancelar(id));
	}

}
