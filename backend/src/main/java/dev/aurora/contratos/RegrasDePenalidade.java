package dev.aurora.contratos;

import java.util.List;
import java.util.UUID;

/** API do módulo de contratos para o simulador de penalidades (ADR 0035). */
public interface RegrasDePenalidade {

	/**
	 * Regras calculáveis aferidas na unidade (só as de penalidades ainda aprovadas e
	 * visíveis no escopo atual).
	 */
	List<RegraDePenalidade> aferidasNa(UUID unidadeId);

	/**
	 * Todas as regras calculáveis visíveis no escopo atual (de penalidades aprovadas).
	 */
	List<RegraDePenalidade> visiveis();

}
