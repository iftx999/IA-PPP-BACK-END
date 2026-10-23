package dev.aurora.mapa.controller;

import dev.aurora.mapa.model.Area;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Área visível do mapa, em graus (lon/lat), limitada ao entorno do Brasil.
 */
record AreaParametro(@NotNull @DecimalMin("-75") @DecimalMax("-28") @Nullable Double oeste,
		@NotNull @DecimalMin("-35") @DecimalMax("7") @Nullable Double sul,
		@NotNull @DecimalMin("-75") @DecimalMax("-28") @Nullable Double leste,
		@NotNull @DecimalMin("-35") @DecimalMax("7") @Nullable Double norte) {

	@AssertTrue(message = "a área precisa ter oeste < leste e sul < norte")
	boolean isOrdenada() {
		return oeste == null || sul == null || leste == null || norte == null || (oeste < leste && sul < norte);
	}

	Area area() {
		return new Area(oeste, sul, leste, norte);
	}

}
