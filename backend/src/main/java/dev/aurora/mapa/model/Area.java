package dev.aurora.mapa.model;

/**
 * Retângulo em graus (SIRGAS 2000, SRID 4674): a área visível do mapa. Limitado ao
 * entorno do Brasil e a um tamanho que o mapa mostra com imóveis.
 */
public record Area(double oeste, double sul, double leste, double norte) {

	/** Lado máximo, em graus (~110 km): acima disso o mapa mostra agrupamentos. */
	public static final double LADO_MAXIMO = 1.0;

	public Area {
		if (!(oeste < leste && sul < norte)) {
			throw new IllegalArgumentException("a área precisa ter oeste < leste e sul < norte");
		}
		if (oeste < -75 || leste > -28 || sul < -35 || norte > 7) {
			throw new IllegalArgumentException("a área fica fora do Brasil");
		}
	}

	public boolean grandeDemais() {
		return leste - oeste > LADO_MAXIMO || norte - sul > LADO_MAXIMO;
	}

}
