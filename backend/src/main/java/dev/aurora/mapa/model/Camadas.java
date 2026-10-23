package dev.aurora.mapa.model;

import java.time.YearMonth;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Camadas leves do mapa (polígonos e pontos de poucas unidades), entregues de uma vez;
 * imóveis e rede vêm por tile.
 */
public record Camadas(@Nullable YearMonth competencia, List<YearMonth> competencias, List<CamadaMunicipio> municipios,
		List<CamadaSetor> setores, List<UnidadeOperacional> operacionais, @Nullable Area enquadramento) {
}
