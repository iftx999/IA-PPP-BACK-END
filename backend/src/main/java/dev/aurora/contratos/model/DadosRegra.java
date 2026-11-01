package dev.aurora.contratos.model;

import java.math.BigDecimal;

import dev.aurora.contratos.ContagemPontos;
import dev.aurora.contratos.FormulaPenalidade;
import dev.aurora.contratos.PeriodicidadePenalidade;
import org.jspecify.annotations.Nullable;

/** O que se informa ao tornar uma penalidade calculável (ADR 0035). */
public record DadosRegra(FormulaPenalidade formula, BigDecimal valor, @Nullable BigDecimal baseValor,
		@Nullable String baseDescricao, ContagemPontos contagem, PeriodicidadePenalidade periodicidade,
		@Nullable BigDecimal teto) {
}
