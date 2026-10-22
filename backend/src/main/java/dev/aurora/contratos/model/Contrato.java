package dev.aurora.contratos.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Contrato com a unidade que ele cobre e os totais para a lista. */
public record Contrato(UUID id, String numero, UUID unidadeId, String unidadeNome, @Nullable String objeto,
		@Nullable String poderConcedente, @Nullable String regulador, @Nullable LocalDate dataAssinatura,
		@Nullable LocalDate vigenciaFim, long documentos, long itensPendentes, long itensAprovados) {
}
