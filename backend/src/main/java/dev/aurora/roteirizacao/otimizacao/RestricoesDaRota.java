package dev.aurora.roteirizacao.otimizacao;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Regras da RN-25. Duras (nunca quebradas no resultado viável): compatibilidade da equipe
 * (RN-23) e a rota dentro da jornada. Médias, na ordem da RN-25: (1) emergencial sem
 * rota; (2) OS que estoura o prazo; (3) alta, média e baixa sem rota, nesta ordem.
 * Flexíveis: (1) emergenciais cedo; (4) menos deslocamento; (5) carga equilibrada entre
 * as equipes.
 */
public class RestricoesDaRota implements ConstraintProvider {

	static final long PESO_EMERGENCIAL_SEM_ROTA = 1_000_000;

	static final long PESO_PRAZO_ESTOURADO = 10_000;

	/** Por minuto de espera até a emergencial: pesa mais que 16 min de deslocamento. */
	static final long PESO_ESPERA_EMERGENCIAL = 1_000;

	/** Por minuto de desequilíbrio (desvio da carga das equipes). */
	static final long PESO_DESEQUILIBRIO = 10;

	@Override
	public Constraint[] defineConstraints(ConstraintFactory fabrica) {
		return new Constraint[] { incompativel(fabrica), alemDaJornada(fabrica), semRota(fabrica), atrasada(fabrica),
				venceSemRota(fabrica), esperaDasEmergenciais(fabrica), deslocamento(fabrica), equilibrio(fabrica) };
	}

	Constraint incompativel(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.filter(r -> r.incompativeis() > 0)
			.penalize(HardMediumSoftScore.ofHard(1_000), RotaDaEquipe::incompativeis)
			.asConstraint("RN-23 equipe incompativel");
	}

	Constraint alemDaJornada(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.filter(r -> r.excessoSeg() > 0)
			.penalize(HardMediumSoftScore.ONE_HARD, r -> (r.excessoSeg() + 59) / 60)
			.asConstraint("Rota alem da jornada");
	}

	Constraint semRota(ConstraintFactory fabrica) {
		return fabrica.forEachIncludingUnassigned(Parada.class)
			.filter(p -> p.getRota() == null)
			.penalize(HardMediumSoftScore.ONE_MEDIUM, p -> pesoSemRota(p.getPrioridade()))
			.asConstraint("OS sem rota");
	}

	Constraint atrasada(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.filter(r -> r.atrasadas() > 0)
			.penalize(HardMediumSoftScore.ofMedium(PESO_PRAZO_ESTOURADO), RotaDaEquipe::atrasadas)
			.asConstraint("OS chega depois do prazo");
	}

	Constraint venceSemRota(ConstraintFactory fabrica) {
		return fabrica.forEachIncludingUnassigned(Parada.class)
			.filter(Parada::venceSemRota)
			.penalize(HardMediumSoftScore.ofMedium(PESO_PRAZO_ESTOURADO))
			.asConstraint("OS sem rota vence no dia");
	}

	Constraint esperaDasEmergenciais(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.filter(r -> r.esperaDasEmergenciaisMin() > 0)
			.penalize(HardMediumSoftScore.ofSoft(PESO_ESPERA_EMERGENCIAL), RotaDaEquipe::esperaDasEmergenciaisMin)
			.asConstraint("Emergenciais primeiro");
	}

	Constraint deslocamento(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.penalize(HardMediumSoftScore.ONE_SOFT, RotaDaEquipe::deslocamentoSeg)
			.asConstraint("Tempo de deslocamento");
	}

	Constraint equilibrio(ConstraintFactory fabrica) {
		return fabrica.forEach(RotaDaEquipe.class)
			.groupBy(ConstraintCollectors.loadBalance(r -> r, RotaDaEquipe::trabalhoMin))
			.penalize(HardMediumSoftScore.ofSoft(PESO_DESEQUILIBRIO), c -> c.unfairness().longValue())
			.asConstraint("Carga equilibrada");
	}

	static long pesoSemRota(PrioridadeDaParada prioridade) {
		return switch (prioridade) {
			case EMERGENCIAL -> PESO_EMERGENCIAL_SEM_ROTA;
			case ALTA -> 100;
			case MEDIA -> 10;
			case BAIXA -> 1;
		};
	}

}
