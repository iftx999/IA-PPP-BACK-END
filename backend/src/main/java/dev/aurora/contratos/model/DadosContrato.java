package dev.aurora.contratos.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record DadosContrato(String numero, UUID unidadeId, @Nullable String objeto, @Nullable String poderConcedente,
		@Nullable String regulador, @Nullable LocalDate dataAssinatura, @Nullable LocalDate vigenciaFim) {
}
