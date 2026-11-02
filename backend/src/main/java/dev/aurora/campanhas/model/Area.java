package dev.aurora.campanhas.model;

/**
 * Retângulo visível do mapa, em graus (SIRGAS 2000), como na exportação da área (ADR
 * 0017).
 */
public record Area(double oeste, double sul, double leste, double norte) {

	public Area {
		if (!(oeste < leste && sul < norte)) {
			throw new IllegalArgumentException("a área precisa ter oeste < leste e sul < norte");
		}
		if (oeste < -75 || leste > -28 || sul < -35 || norte > 7) {
			throw new IllegalArgumentException("a área fica fora do Brasil");
		}
	}

}
