package dev.aurora.mapa.model;

import java.math.BigDecimal;
import java.util.UUID;

import dev.aurora.adesao.Situacao;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Município do escopo com a malha do IBGE e a situação da adesão na competência. */
public record CamadaMunicipio(UUID unidadeId, String nome, String codigoIbge, @Nullable BigDecimal adesao,
		Situacao situacao, JsonNode geometria) {
}
