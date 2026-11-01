package dev.aurora.simulador.controller;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.contexto.ExigePermissao;
import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import dev.aurora.contratos.RegraDePenalidade;
import dev.aurora.simulador.model.Cenario;
import dev.aurora.simulador.model.Simulacao;
import dev.aurora.simulador.service.SimuladorService;
import org.jspecify.annotations.Nullable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Simulador de penalidades (ADR 0035). Só leitura: nada é gravado nem auditado. */
@RestController
@RequestMapping("/api/v1/simulador")
@ExigePermissao("indicador:ler")
class SimuladorController {

	/**
	 * @param ligacoesNecessariasPorMes ritmo constante que atinge a meta no prazo; nulo
	 * sem prazo futuro
	 * @param regrasVisiveis falso quando a pessoa não consulta contratos aqui (sem multa
	 * estimada)
	 */
	record SimulacaoResponse(UUID unidadeId, String unidadeNome, String unidadeTipo, YearMonth competencia,
			long ligados, long comRede, BigDecimal adesao, @Nullable BigDecimal ritmoAtual,
			@Nullable MetaSimuladaResponse meta, List<MesMedidoResponse> serie,
			@Nullable BigDecimal ligacoesNecessariasPorMes, YearMonth fim, boolean regrasVisiveis,
			List<UnidadeComRegraResponse> unidadesComRegra, List<RegraSimuladaResponse> regras,
			CenarioResponse ritmoAtualProjetado, CenarioResponse cenario) {

		static SimulacaoResponse de(Simulacao s) {
			var base = s.base();
			var atual = base.atual();
			var multasAtual = s.ritmoAtual().multasPorRegra();
			var multasCenario = s.cenario().multasPorRegra();
			return new SimulacaoResponse(base.unidadeId(), base.unidadeNome(), base.tipo(), base.competencia(),
					atual.ligados(), atual.comRede(), s.cenario().meses().getFirst().adesao(), base.ritmoAtual(),
					MetaSimuladaResponse.de(base.meta()),
					base.serie()
						.stream()
						.map(m -> new MesMedidoResponse(m.competencia(), m.ligados(), m.comRede()))
						.toList(),
					s.ligacoesNecessariasPorMes(), s.fim(), s.regrasVisiveis(),
					s.unidadesComRegra()
						.stream()
						.map(u -> new UnidadeComRegraResponse(u.id(), u.nome(), u.regras()))
						.toList(),
					s.regras().stream().map(r -> RegraSimuladaResponse.de(r, multasAtual, multasCenario)).toList(),
					CenarioResponse.de(s.ritmoAtual()), CenarioResponse.de(s.cenario()));
		}
	}

	record MetaSimuladaResponse(BigDecimal valor, @Nullable YearMonth prazo, UUID unidadeId, String unidadeNome,
			boolean herdada, @Nullable String origem) {

		static @Nullable MetaSimuladaResponse de(BaseDaSimulacao.@Nullable MetaDaSimulacao m) {
			return m == null ? null : new MetaSimuladaResponse(m.valor(), m.prazo(), m.unidadeId(), m.unidadeNome(),
					m.herdada(), m.origem());
		}
	}

	record UnidadeComRegraResponse(UUID id, String nome, int regras) {
	}

	record MesMedidoResponse(YearMonth competencia, long ligados, long comRede) {
	}

	/**
	 * @param multaRitmoAtual total estimado no horizonte no ritmo atual
	 */
	record RegraSimuladaResponse(UUID itemId, UUID contratoId, String contratoNumero, String titulo,
			@Nullable String clausula, int pagina, FormulaPenalidade formula, BigDecimal valor,
			@Nullable BigDecimal baseValor, @Nullable String baseDescricao, ContagemPontos contagem,
			PeriodicidadePenalidade periodicidade, @Nullable BigDecimal teto, BigDecimal multaRitmoAtual,
			BigDecimal multaCenario) {

		static RegraSimuladaResponse de(RegraDePenalidade r, List<Cenario.MultaDaRegra> atual,
				List<Cenario.MultaDaRegra> cenario) {
			return new RegraSimuladaResponse(r.itemId(), r.contratoId(), r.contratoNumero(), r.titulo(), r.clausula(),
					r.pagina(), r.formula(), r.valor(), r.baseValor(), r.baseDescricao(), r.contagem(),
					r.periodicidade(), r.teto(), total(atual, r.itemId()), total(cenario, r.itemId()));
		}

		private static BigDecimal total(List<Cenario.MultaDaRegra> multas, UUID item) {
			return multas.stream()
				.filter(m -> m.itemId().equals(item))
				.map(Cenario.MultaDaRegra::total)
				.findFirst()
				.orElse(BigDecimal.ZERO);
		}
	}

	record CenarioResponse(BigDecimal ligacoesPorMes, @Nullable YearMonth mesAtingimento,
			@Nullable BigDecimal adesaoNoPrazo, BigDecimal multaTotal, List<MesProjetadoResponse> meses) {

		static CenarioResponse de(Cenario c) {
			return new CenarioResponse(c.ligacoesPorMes(), c.mesAtingimento(), c.adesaoNoPrazo(), c.multaTotal(),
					c.meses()
						.stream()
						.map(m -> new MesProjetadoResponse(m.competencia(), m.ligados(), m.adesao(), m.medido(),
								m.pontosAbaixo(), m.aferido(), m.multa()))
						.toList());
		}
	}

	record MesProjetadoResponse(YearMonth competencia, long ligados, BigDecimal adesao, boolean medido,
			BigDecimal pontosAbaixo, boolean aferido, BigDecimal multa) {
	}

	private final SimuladorService simulador;

	SimuladorController(SimuladorService simulador) {
		this.simulador = simulador;
	}

	/**
	 * @param unidade omitida = a primeira com penalidade calculável (ou a mais alta do
	 * escopo)
	 * @param ligacoesPorMes ritmo do cenário; omitido = o necessário para atingir a meta
	 * no prazo
	 * @param mesesAposPrazo horizonte depois do prazo (0–60, padrão 12)
	 */
	@GetMapping
	SimulacaoResponse simular(@RequestParam(required = false) @Nullable UUID unidade,
			@RequestParam(required = false) @Nullable BigDecimal ligacoesPorMes,
			@RequestParam(required = false) @Nullable Integer mesesAposPrazo) {
		return SimulacaoResponse.de(simulador.simular(unidade, ligacoesPorMes, mesesAposPrazo));
	}

}
