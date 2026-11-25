package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Conflito de campo para o programador decidir (J11; 10.9.5). */
public record Conflito(UUID id, UUID osId, long osNumero, String servicoNome, StatusOrdem osStatus,
		@Nullable String equipeNome, TipoConflito tipo, String descricao, AcaoDeCampo acao, String agente,
		Instant ocorridoEm, Instant criadoEm, @Nullable String decisao, @Nullable String decididoPor,
		@Nullable Instant decididoEm) {
}
