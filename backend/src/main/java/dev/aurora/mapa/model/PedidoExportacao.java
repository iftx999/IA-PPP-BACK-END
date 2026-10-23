package dev.aurora.mapa.model;

import org.jspecify.annotations.Nullable;

/**
 * O que está visível no mapa (RN-12): área, imóveis pelas camadas ligadas (nulo quando a
 * camada está desligada), marcações e, no PDF, a imagem do mapa como a pessoa o vê.
 */
public record PedidoExportacao(FormatoExportacao formato, Area area, @Nullable FiltroImoveis imoveis, boolean marcacoes,
		byte @Nullable [] imagemPng) {
}
