package dev.aurora.logradouros.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Trecho do logradouro.
 *
 * @param coordenadas pontos da linha, [longitude, latitude]
 */
public record Trecho(UUID id, String codigo, BigDecimal extensaoM, @Nullable Integer numeroInicial,
		@Nullable Integer numeroFinal, Lado lado, long imoveis, List<double[]> coordenadas) {
}
