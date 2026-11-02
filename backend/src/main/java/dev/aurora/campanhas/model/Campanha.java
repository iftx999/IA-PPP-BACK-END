package dev.aurora.campanhas.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Campanha de ligação com o recorte que a originou e o seu funil. */
public record Campanha(UUID id, UUID unidadeId, String unidadeNome, String nome, Recorte recorte, AcaoCampanha acao,
		YearMonth competencia, LocalDate prazo, StatusCampanha status, long ligadosNaCriacao, long comRedeNaCriacao,
		String criadaPor, Instant criadaEm, @Nullable Instant finalizadaEm, Funil funil) {
}
