package dev.aurora.logradouros.model;

import org.jspecify.annotations.Nullable;

/**
 * Trecho lido do GeoJSON, já validado.
 *
 * @param geometria a geometria da feição em GeoJSON (LineString ou MultiLineString)
 */
public record FeicaoTrecho(int posicao, String codigoLogradouro, @Nullable String tipo, String nome,
		@Nullable String bairro, String codigoTrecho, @Nullable Integer numeroInicial, @Nullable Integer numeroFinal,
		Lado lado, String geometria) {
}
