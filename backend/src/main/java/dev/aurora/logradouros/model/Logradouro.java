package dev.aurora.logradouros.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Logradouro com os totais dos trechos e dos imóveis vinculados. */
public record Logradouro(UUID id, UUID unidadeId, String unidadeNome, String codigo, @Nullable String tipo, String nome,
		@Nullable String bairro, long trechos, BigDecimal extensaoM, long imoveis) {
}
