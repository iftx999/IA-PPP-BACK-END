package dev.aurora.campanhas;

import java.util.List;

/** API do módulo de campanhas para os demais módulos (alertas). */
public interface Campanhas {

	/**
	 * Campanhas do escopo com ao menos uma OS divergente, da que tem mais para a que tem
	 * menos.
	 */
	List<CampanhaDivergente> comOrdensDivergentes();

}
