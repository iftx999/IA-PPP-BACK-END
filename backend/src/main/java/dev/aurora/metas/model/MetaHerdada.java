package dev.aurora.metas.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Meta seguida de uma unidade acima (RN-06): "Segue Cidade A · 85,0%". */
public record MetaHerdada(UUID unidadeId, String unidadeNome, BigDecimal valor, @Nullable BigDecimal alerta) {
}
