package dev.aurora.mapa.controller;

import dev.aurora.mapa.model.Area;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Área visível do mapa, em graus (lon/lat), limitada ao entorno do Brasil.
 */
record AreaParametro(@DecimalMin("-75") @DecimalMax("-28") double oeste, @DecimalMin("-35") @DecimalMax("7") double sul,
		@DecimalMin("-75") @DecimalMax("-28") double leste, @DecimalMin("-35") @DecimalMax("7") double norte) {

	@AssertTrue(message = "a área precisa ter oeste < leste e sul < norte")
	boolean isOrdenada() {
		return oeste < leste && sul < norte;
	}

	Area area() {
		return new Area(oeste, sul, leste, norte);
	}

}
