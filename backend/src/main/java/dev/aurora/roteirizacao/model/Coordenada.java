package dev.aurora.roteirizacao.model;

/** Ponto em graus decimais (SIRGAS 2000, tratado como WGS 84). */
public record Coordenada(double latitude, double longitude) {

	private static final double RAIO_DA_TERRA_M = 6_371_008.8;

	public Coordenada {
		if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90
				|| Math.abs(longitude) > 180) {
			throw new IllegalArgumentException("coordenada inválida");
		}
	}

	/** Distância em linha reta (haversine), em metros. */
	public double distanciaM(Coordenada outra) {
		var dLat = Math.toRadians(outra.latitude - latitude);
		var dLon = Math.toRadians(outra.longitude - longitude);
		var a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(latitude))
				* Math.cos(Math.toRadians(outra.latitude)) * Math.pow(Math.sin(dLon / 2), 2);
		return 2 * RAIO_DA_TERRA_M * Math.asin(Math.min(1, Math.sqrt(a)));
	}

}
