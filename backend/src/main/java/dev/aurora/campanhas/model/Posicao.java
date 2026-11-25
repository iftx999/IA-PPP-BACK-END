package dev.aurora.campanhas.model;

import dev.aurora.comum.RegraVioladaException;
import org.jspecify.annotations.Nullable;

/**
 * Posição do GPS do celular (WGS 84, tratado como SIRGAS 2000: diferença de centímetros).
 *
 * @param precisaoM raio de incerteza informado pelo aparelho, em metros
 */
public record Posicao(double latitude, double longitude, @Nullable Double precisaoM) {

	public Posicao {
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90
				|| Math.abs(longitude) > 180) {
			throw new RegraVioladaException("Posição inválida.");
		}
		if (precisaoM != null && (!Double.isFinite(precisaoM) || precisaoM < 0 || precisaoM > 100_000)) {
			throw new RegraVioladaException("Precisão da posição inválida.");
		}
	}

}
