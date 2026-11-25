package dev.aurora.roteirizacao.otimizacao;

import java.time.Duration;

import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

/**
 * Resolve o {@link PlanoDoDia} com o Timefold Solver: heurística de construção e busca
 * local até o tempo limite ou até ficar {@code semMelhora} sem achar solução melhor.
 */
public class Otimizador {

	private final SolverFactory<PlanoDoDia> base;

	public Otimizador() {
		this.base = SolverFactory.create(configuracao(Duration.ofSeconds(1), Duration.ofSeconds(1)));
	}

	public PlanoDoDia resolver(PlanoDoDia problema, Duration limite, Duration semMelhora) {
		if (problema.getParadas().isEmpty() || problema.getRotas().isEmpty()) {
			return problema;
		}
		var termino = new TerminationConfig().withSpentLimit(limite)
			.withUnimprovedSpentLimit(semMelhora.compareTo(limite) < 0 ? semMelhora : limite);
		return base.buildSolver(new SolverConfigOverride().withTerminationConfig(termino)).solve(problema);
	}

	static SolverConfig configuracao(Duration limite, Duration semMelhora) {
		return new SolverConfig().withSolutionClass(PlanoDoDia.class)
			.withEntityClasses(RotaDaEquipe.class, Parada.class)
			.withConstraintProviderClass(RestricoesDaRota.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(limite).withUnimprovedSpentLimit(semMelhora));
	}

}
