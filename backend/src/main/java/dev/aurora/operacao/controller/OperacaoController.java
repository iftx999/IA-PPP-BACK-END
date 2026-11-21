package dev.aurora.operacao.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.aurora.contexto.ExigePermissao;
import dev.aurora.operacao.model.EquipeNoDia;
import dev.aurora.operacao.model.Indicadores;
import dev.aurora.operacao.model.MapaDoDia;
import dev.aurora.operacao.model.OrdemNoDia;
import dev.aurora.operacao.model.Ponto;
import dev.aurora.operacao.model.Sugestao;
import dev.aurora.operacao.service.IndicadoresDaOperacaoService;
import dev.aurora.operacao.service.OperacaoDoDiaService;
import dev.aurora.operacao.service.SugestoesDeReotimizacaoService;
import org.jspecify.annotations.Nullable;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acompanhamento da operação de campo (J12, J7; ADR 0049): mapa do dia, indicadores e
 * sugestões de reotimização. Tempos e atrasos são estimativas (RN-25).
 */
@RestController
@RequestMapping("/api/v1/operacao")
@ExigePermissao("os:programar")
class OperacaoController {

	record PontoDaOperacaoDto(double latitude, double longitude) {

		static @Nullable PontoDaOperacaoDto de(@Nullable Ponto p) {
			return p == null ? null : new PontoDaOperacaoDto(p.latitude(), p.longitude());
		}

	}

	record EquipeNoDiaResponse(UUID id, String nome, UUID unidadeId, boolean emJornada, @Nullable Instant jornadaInicio,
			@Nullable Instant jornadaFim, @Nullable PontoDaOperacaoDto posicao, @Nullable Instant posicaoEm,
			@Nullable Double precisaoM, @Nullable Integer atrasoMin, @Nullable Instant fimPrevisto) {

		static EquipeNoDiaResponse de(EquipeNoDia e) {
			return new EquipeNoDiaResponse(e.id(), e.nome(), e.unidadeId(), e.emJornada(), e.jornadaInicio(),
					e.jornadaFim(), PontoDaOperacaoDto.de(e.posicao()), e.posicaoEm(), e.precisaoM(), e.atrasoMin(),
					e.fimPrevisto());
		}

	}

	record OrdemNoDiaResponse(UUID id, long numero, String servicoNome, String status, String prioridade,
			@Nullable UUID equipeId, @Nullable Integer sequenciaRota, @Nullable PontoDaOperacaoDto ponto,
			@Nullable Instant prazoEm, boolean vencida) {

		static OrdemNoDiaResponse de(OrdemNoDia o) {
			return new OrdemNoDiaResponse(o.id(), o.numero(), o.servicoNome(), o.status(), o.prioridade(), o.equipeId(),
					o.sequenciaRota(), PontoDaOperacaoDto.de(o.ponto()), o.prazoEm(), o.vencida());
		}

	}

	record MapaDoDiaResponse(LocalDate data, Instant geradoEm, List<EquipeNoDiaResponse> equipes,
			List<OrdemNoDiaResponse> ordens, int sugestoesAbertas) {

		static MapaDoDiaResponse de(MapaDoDia m) {
			return new MapaDoDiaResponse(m.data(), m.geradoEm(),
					m.equipes().stream().map(EquipeNoDiaResponse::de).toList(),
					m.ordens().stream().map(OrdemNoDiaResponse::de).toList(), m.sugestoesAbertas());
		}

	}

	record ResumoDaOperacaoResponse(int ordens, int executadas, int naoExecutadas, int devolvidas, int noPrazo,
			int foraDoPrazo, int vencidasAbertas, @Nullable Double deslocamentoMedioMin,
			@Nullable Double execucaoMediaMin, double km) {

		static ResumoDaOperacaoResponse de(Indicadores.Resumo r) {
			return new ResumoDaOperacaoResponse(r.ordens(), r.executadas(), r.naoExecutadas(), r.devolvidas(),
					r.noPrazo(), r.foraDoPrazo(), r.vencidasAbertas(), r.deslocamentoMedioMin(), r.execucaoMediaMin(),
					r.km());
		}

	}

	record PrazoDoServicoResponse(UUID servicoId, String servico, int noPrazo, int foraDoPrazo, int vencidasAbertas,
			int abertasNoPrazo) {

		static PrazoDoServicoResponse de(Indicadores.PrazoDoServico p) {
			return new PrazoDoServicoResponse(p.servicoId(), p.servico(), p.noPrazo(), p.foraDoPrazo(),
					p.vencidasAbertas(), p.abertasNoPrazo());
		}

	}

	record TempoDoServicoResponse(UUID servicoId, String servico, String unidadeMedida, boolean tempoPorUnidade,
			int amostras, double execucaoMediaMin, double padraoMedioMin, @Nullable Double razaoMediana,
			double tempoPadraoAtual, @Nullable Double tempoSugerido) {

		static TempoDoServicoResponse de(Indicadores.TempoDoServico t) {
			return new TempoDoServicoResponse(t.servicoId(), t.servico(), t.unidadeMedida(), t.tempoPorUnidade(),
					t.amostras(), t.execucaoMediaMin(), t.padraoMedioMin(), t.razaoMediana(), t.tempoPadraoAtual(),
					t.tempoSugerido());
		}

	}

	record MotivoDaOperacaoResponse(String tipo, String codigo, String nome, int quantidade) {

		static MotivoDaOperacaoResponse de(Indicadores.ContagemPorMotivo m) {
			return new MotivoDaOperacaoResponse(m.tipo(), m.codigo(), m.nome(), m.quantidade());
		}

	}

	record ProdutividadeDaEquipeResponse(UUID equipeId, String equipe, int executadas, int naoExecutadas,
			int devolvidas, double horasDeJornada, @Nullable Double osPorHora, double km, @Nullable Double kmPorOs,
			@Nullable Double deslocamentoMedioMin) {

		static ProdutividadeDaEquipeResponse de(Indicadores.ProdutividadeDaEquipe e) {
			return new ProdutividadeDaEquipeResponse(e.equipeId(), e.equipe(), e.executadas(), e.naoExecutadas(),
					e.devolvidas(), e.horasDeJornada(), e.osPorHora(), e.km(), e.kmPorOs(), e.deslocamentoMedioMin());
		}

	}

	record IndicadoresResponse(LocalDate de, LocalDate ate, ResumoDaOperacaoResponse resumo,
			List<PrazoDoServicoResponse> prazos, List<TempoDoServicoResponse> tempos,
			List<MotivoDaOperacaoResponse> motivos, List<ProdutividadeDaEquipeResponse> equipes) {

		static IndicadoresResponse de(Indicadores i) {
			return new IndicadoresResponse(i.de(), i.ate(), ResumoDaOperacaoResponse.de(i.resumo()),
					i.prazos().stream().map(PrazoDoServicoResponse::de).toList(),
					i.tempos().stream().map(TempoDoServicoResponse::de).toList(),
					i.motivos().stream().map(MotivoDaOperacaoResponse::de).toList(),
					i.equipes().stream().map(ProdutividadeDaEquipeResponse::de).toList());
		}

	}

	record SugestaoReotimizacaoResponse(UUID id, UUID unidadeId, LocalDate data, String gatilho,
			@Nullable UUID equipeId, @Nullable String equipeNome, @Nullable UUID osId, @Nullable Long osNumero,
			String descricao, @Nullable Integer atrasoMin, String situacao, Instant criadaEm,
			@Nullable Instant decididaEm, @Nullable UUID roteirizacaoId) {

		static SugestaoReotimizacaoResponse de(Sugestao s) {
			return new SugestaoReotimizacaoResponse(s.id(), s.unidadeId(), s.data(), s.gatilho().name(), s.equipeId(),
					s.equipeNome(), s.osId(), s.osNumero(), s.descricao(), s.atrasoMin(), s.situacao().name(),
					s.criadaEm(), s.decididaEm(), s.roteirizacaoId());
		}

	}

	private final OperacaoDoDiaService dia;

	private final IndicadoresDaOperacaoService indicadores;

	private final SugestoesDeReotimizacaoService sugestoes;

	OperacaoController(OperacaoDoDiaService dia, IndicadoresDaOperacaoService indicadores,
			SugestoesDeReotimizacaoService sugestoes) {
		this.dia = dia;
		this.indicadores = indicadores;
		this.sugestoes = sugestoes;
	}

	/** Mapa do dia: equipes (última posição, atraso estimado) e OS por situação. */
	@GetMapping("/dia")
	MapaDoDiaResponse mapa(@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate data) {
		return MapaDoDiaResponse.de(dia.mapa(unidade, data));
	}

	/** Indicadores do período (até 92 dias; padrão: os últimos 30). */
	@GetMapping("/indicadores")
	IndicadoresResponse indicadores(@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate ate) {
		return IndicadoresResponse.de(indicadores.calcular(unidade, de, ate));
	}

	@GetMapping("/sugestoes")
	List<SugestaoReotimizacaoResponse> sugestoes(@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate data) {
		return sugestoes.doDia(unidade, data).stream().map(SugestaoReotimizacaoResponse::de).toList();
	}

	/**
	 * Aceita a sugestão (e as outras abertas da mesma unidade): pede a reotimização, que
	 * o programador confere e publica na roteirização.
	 */
	@PostMapping("/sugestoes/{id}/aceitar")
	SugestaoReotimizacaoResponse aceitar(@PathVariable UUID id) {
		return SugestaoReotimizacaoResponse.de(sugestoes.aceitar(id));
	}

	@PostMapping("/sugestoes/{id}/ignorar")
	SugestaoReotimizacaoResponse ignorar(@PathVariable UUID id) {
		return SugestaoReotimizacaoResponse.de(sugestoes.ignorar(id));
	}

}
