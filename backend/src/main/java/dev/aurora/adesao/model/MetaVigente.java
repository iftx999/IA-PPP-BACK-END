package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Meta aplicável à unidade na competência (RN-06). Quando a unidade não tem meta própria,
 * vale a da unidade acima mais próxima: {@code herdada} indica isso e
 * {@code unidadeId}/{@code unidadeNome} apontam de onde veio. {@code origem} é o rótulo
 * da cláusula (ex.: "Contrato 001/2025, cláusula 7.2, p. 2"); nulo = meta manual.
 */
public record MetaVigente(UUID metaId, UUID unidadeId, String unidadeNome, BigDecimal valorMeta, BigDecimal valorAlerta,
		LocalDate vigenciaInicio, LocalDate vigenciaFim, String origem, boolean herdada) {
}
