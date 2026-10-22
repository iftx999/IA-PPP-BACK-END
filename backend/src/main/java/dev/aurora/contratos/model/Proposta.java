package dev.aurora.contratos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * Item como a IA o devolve, antes da validação: a unidade alvo vem por nome e tudo pode
 * estar errado (a validação descarta ou limpa).
 */
public record Proposta(String tipo, String titulo, String resumo, @Nullable String clausula, int pagina, String trecho,
		@Nullable BigDecimal valorReferencia, @Nullable String unidadeMedida, @Nullable LocalDate dataLimite,
		@Nullable String periodicidade, @Nullable String unidadeAlvo, @Nullable BigDecimal valorAlerta,
		@Nullable LocalDate vigenciaInicio, @Nullable BigDecimal confianca) {
}
