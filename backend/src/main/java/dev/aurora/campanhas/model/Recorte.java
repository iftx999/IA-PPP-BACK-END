package dev.aurora.campanhas.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Imóveis sem ligação que entram na campanha (I2), na competência mais recente.
 *
 * @param unidadeId unidade da campanha (e as de baixo); na origem setor, o setor
 * @param situacao FACTIVEL ou CORTADO; nula = os dois
 * @param motivoCorte INADIMPLENCIA ou A_PEDIDO (só cortados); nulo = qualquer
 * @param area só na origem área do mapa
 */
public record Recorte(OrigemCampanha origem, UUID unidadeId, @Nullable String situacao, @Nullable String motivoCorte,
		@Nullable Area area) {
}
