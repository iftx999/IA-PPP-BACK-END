package dev.aurora.contratos.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Item extraído de um documento. {@code propostaIa} guarda o JSON original da IA (nulo
 * para itens manuais), para comparar com o valor revisado. {@code substituidoPor}: item
 * do aditivo que tomou o lugar deste (A5).
 */
public record ItemContratual(UUID id, UUID contratoId, UUID documentoId, DadosItem dados, @Nullable String unidadeNome,
		StatusItem status, String origem, @Nullable String modeloIa, @Nullable BigDecimal confianca,
		@Nullable String propostaIa, Instant propostoEm, @Nullable String revisadoPor, @Nullable Instant revisadoEm,
		@Nullable String motivoRevisao, @Nullable UUID substituidoPor) {
}
