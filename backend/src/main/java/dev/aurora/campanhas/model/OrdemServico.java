package dev.aurora.campanhas.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Ordem de serviço (I3). Localização (se o imóvel tiver) para achar o imóvel no campo.
 *
 * @param atrasada em aberto depois do prazo (ou da data reagendada)
 */
public record OrdemServico(UUID id, UUID campanhaId, String campanhaNome, int numero, String matricula, UUID unidadeId,
		String unidadeNome, TipoOrdem tipo, String situacaoNaCriacao, Prioridade prioridade, LocalDate prazo,
		@Nullable UUID equipeId, @Nullable String equipeNome, @Nullable Instant enviadaEm, StatusOrdem status,
		@Nullable MotivoResultado motivo, @Nullable String observacao, @Nullable LocalDate reagendadaPara,
		@Nullable Instant resultadoEm, @Nullable String resultadoPor, Conferencia conferencia,
		@Nullable YearMonth confirmadaEm, boolean atrasada, @Nullable Double latitude, @Nullable Double longitude) {

	public OrdemServico comMatricula(String outra) {
		return new OrdemServico(id, campanhaId, campanhaNome, numero, outra, unidadeId, unidadeNome, tipo,
				situacaoNaCriacao, prioridade, prazo, equipeId, equipeNome, enviadaEm, status, motivo, observacao,
				reagendadaPara, resultadoEm, resultadoPor, conferencia, confirmadaEm, atrasada, latitude, longitude);
	}

}
