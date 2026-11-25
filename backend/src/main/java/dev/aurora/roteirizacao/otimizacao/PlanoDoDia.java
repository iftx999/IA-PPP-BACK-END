package dev.aurora.roteirizacao.otimizacao;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import org.jspecify.annotations.Nullable;

/**
 * O problema de um dia: rotas das equipes e as OS a distribuir.
 *
 * @see RestricoesDaRota
 */
@PlanningSolution
public class PlanoDoDia {

	@PlanningEntityCollectionProperty
	private List<RotaDaEquipe> rotas = new ArrayList<>();

	@PlanningEntityCollectionProperty
	@ValueRangeProvider
	private List<Parada> paradas = new ArrayList<>();

	/**
	 * Fim do dia (maior fim de jornada): OS sem rota com prazo até ele estoura o prazo.
	 */
	private long fimDoDiaSeg;

	@PlanningScore
	private @Nullable HardMediumSoftScore pontuacao;

	PlanoDoDia() {
	}

	public PlanoDoDia(List<RotaDaEquipe> rotas, List<Parada> paradas) {
		this.rotas = new ArrayList<>(rotas);
		this.paradas = new ArrayList<>(paradas);
		this.fimDoDiaSeg = rotas.stream().mapToLong(RotaDaEquipe::getFimSeg).max().orElse(0);
		this.paradas.forEach(p -> p.setFimDoDiaSeg(fimDoDiaSeg));
	}

	public List<RotaDaEquipe> getRotas() {
		return rotas;
	}

	public List<Parada> getParadas() {
		return paradas;
	}

	public long getFimDoDiaSeg() {
		return fimDoDiaSeg;
	}

	public @Nullable HardMediumSoftScore getPontuacao() {
		return pontuacao;
	}

	public void setPontuacao(@Nullable HardMediumSoftScore pontuacao) {
		this.pontuacao = pontuacao;
	}

}
