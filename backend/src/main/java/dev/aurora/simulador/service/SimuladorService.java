package dev.aurora.simulador.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.adesao.Medicoes;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.contratos.RegraDePenalidade;
import dev.aurora.contratos.RegrasDePenalidade;
import dev.aurora.simulador.model.Simulacao;
import dev.aurora.simulador.model.Simulacao.UnidadeComRegra;
import dev.aurora.simulador.model.Simulador;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

/**
 * Simulador de penalidades (ADR 0035). A adesão é lida no escopo de
 * {@code indicador:ler}; as regras, só onde a pessoa também tem {@code contrato:ler} (sem
 * isso, a projeção sai e a multa não é estimada).
 */
@Service
public class SimuladorService {

	static final String LER_INDICADORES = "indicador:ler";

	static final String LER_CONTRATOS = "contrato:ler";

	/** Meses projetados depois do prazo, quando não informado. */
	static final int MESES_APOS_PRAZO_PADRAO = 12;

	static final int MESES_APOS_PRAZO_MAXIMO = 60;

	/** Horizonte de metas sem prazo (não há multa; só quando a meta sai). */
	static final int HORIZONTE_SEM_PRAZO = 24;

	/**
	 * Ritmo acima disso é erro de digitação (a maior concessão tem bem menos imóveis).
	 */
	static final BigDecimal LIGACOES_POR_MES_MAXIMO = BigDecimal.valueOf(1_000_000);

	private final Medicoes medicoes;

	private final RegrasDePenalidade regras;

	SimuladorService(Medicoes medicoes, RegrasDePenalidade regras) {
		this.medicoes = medicoes;
		this.regras = regras;
	}

	/**
	 * @param unidadeId nula = a primeira unidade com penalidade calculável; sem nenhuma,
	 * a mais alta do escopo
	 * @param ligacoesPorMes ritmo do cenário; nulo = o necessário para a meta no prazo
	 * (ou o atual, se não houver)
	 */
	public Simulacao simular(@Nullable UUID unidadeId, @Nullable BigDecimal ligacoesPorMes,
			@Nullable Integer mesesAposPrazo) {
		if (ligacoesPorMes != null
				&& (ligacoesPorMes.signum() < 0 || ligacoesPorMes.compareTo(LIGACOES_POR_MES_MAXIMO) > 0)) {
			throw new RegraVioladaException("Informe de 0 a 1.000.000 ligações por mês.");
		}
		int aposPrazo = mesesAposPrazo == null ? MESES_APOS_PRAZO_PADRAO : mesesAposPrazo;
		if (aposPrazo < 0 || aposPrazo > MESES_APOS_PRAZO_MAXIMO) {
			throw new RegraVioladaException("Projete de 0 a " + MESES_APOS_PRAZO_MAXIMO + " meses após o prazo.");
		}

		var contexto = ContextoAcesso.exigir().restritoATodas(LER_INDICADORES, LER_CONTRATOS);
		boolean regrasVisiveis = !contexto.escopos().isEmpty();
		List<RegraDePenalidade> todas = regrasVisiveis ? lerRegras(contexto) : List.of();
		var unidadesComRegra = unidadesComRegra(todas);

		var base = (unidadeId != null ? medicoes.baseDaSimulacao(unidadeId) : padrao(unidadesComRegra))
			.orElseThrow(RecursoNaoEncontradoException::new);
		var aplicaveis = todas.stream().filter(r -> r.unidadeId().equals(base.unidadeId())).toList();

		var fim = fim(base, aposPrazo);
		var necessarias = Simulador.ligacoesNecessariasPorMes(base);
		var ritmoAtual = base.ritmoAtual() == null ? BigDecimal.ZERO : base.ritmoAtual().max(BigDecimal.ZERO);
		var ritmoDoCenario = ligacoesPorMes != null ? ligacoesPorMes : necessarias != null ? necessarias : ritmoAtual;

		return new Simulacao(base, aplicaveis, regrasVisiveis, unidadesComRegra, necessarias, fim,
				Simulador.projetar(base, ritmoAtual, fim, aplicaveis),
				Simulador.projetar(base, ritmoDoCenario, fim, aplicaveis));
	}

	/**
	 * A primeira unidade com regra que tem medição no escopo de indicadores; senão a
	 * padrão.
	 */
	private Optional<BaseDaSimulacao> padrao(List<UnidadeComRegra> unidadesComRegra) {
		for (var unidade : unidadesComRegra) {
			var base = medicoes.baseDaSimulacao(unidade.id());
			if (base.isPresent()) {
				return base;
			}
		}
		return medicoes.baseDaSimulacao(null);
	}

	private static List<UnidadeComRegra> unidadesComRegra(List<RegraDePenalidade> regras) {
		var porUnidade = new LinkedHashMap<UUID, UnidadeComRegra>();
		for (var r : regras) {
			porUnidade.merge(r.unidadeId(), new UnidadeComRegra(r.unidadeId(), r.unidadeNome(), 1),
					(a, b) -> new UnidadeComRegra(a.id(), a.nome(), a.regras() + 1));
		}
		return List.copyOf(porUnidade.values());
	}

	static YearMonth fim(BaseDaSimulacao base, int mesesAposPrazo) {
		var prazo = base.meta() == null ? null : base.meta().prazo();
		if (prazo == null) {
			return base.competencia().plusMonths(HORIZONTE_SEM_PRAZO);
		}
		var referencia = prazo.isAfter(base.competencia()) ? prazo : base.competencia();
		return referencia.plusMonths(mesesAposPrazo);
	}

	private List<RegraDePenalidade> lerRegras(ContextoAcesso contexto) {
		try {
			return ContextoAcesso.executar(contexto, regras::visiveis);
		}
		catch (RuntimeException erro) {
			throw erro;
		}
		catch (Exception erro) {
			throw new IllegalStateException(erro);
		}
	}

}
