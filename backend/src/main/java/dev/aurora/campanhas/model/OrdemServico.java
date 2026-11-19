package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Ordem de serviço (ADR 0036, 0044): serviço e versão do catálogo, alvo (imóvel ou
 * trecho), origem, prazo, programação, resultado e conferência. Endereço e ponto para
 * achar o alvo no campo.
 *
 * @param matricula nula nas OS de trecho
 * @param numeroImovel número no logradouro; vai junto com a matrícula (dado pessoal)
 * @param atrasada em aberto depois do prazo
 */
public record OrdemServico(UUID id, long numero, UUID servicoId, String servicoCodigo, String servicoNome,
		int servicoVersao, String unidadeMedida, AlvoOrdem alvo, OrigemOrdem origem, @Nullable String matricula,
		@Nullable UUID trechoId, @Nullable String trechoCodigo, @Nullable UUID logradouroId,
		@Nullable String logradouroNome, @Nullable String numeroImovel, @Nullable String complemento, UUID unidadeId,
		String unidadeNome, @Nullable UUID campanhaId, @Nullable String campanhaNome, @Nullable UUID loteId,
		@Nullable String situacaoNaCriacao, Prioridade prioridade, Instant prazoEm, @Nullable String instrucoes,
		BigDecimal quantidadePrevista, @Nullable UUID equipeId, @Nullable String equipeNome,
		@Nullable LocalDate dataProgramada, @Nullable Instant enviadaEm, StatusOrdem status,
		@Nullable ResultadoOrdem resultado, @Nullable UUID motivoId, @Nullable String motivoNome,
		@Nullable String observacao, @Nullable BigDecimal quantidadeExecutada, @Nullable Instant resultadoEm,
		@Nullable String resultadoPor, @Nullable Instant movimentadaEm, @Nullable String movimentadaPor,
		Conferencia conferencia, @Nullable YearMonth confirmadaEm, boolean atrasada, Instant criadaEm,
		@Nullable String criadaPor, @Nullable Double latitude, @Nullable Double longitude,
		@Nullable Integer sequenciaRota) {

	/** Sem dados pessoais: matrícula mascarada e sem o número e o complemento. */
	public OrdemServico semDadosPessoais(String matriculaMascarada) {
		return new OrdemServico(id, numero, servicoId, servicoCodigo, servicoNome, servicoVersao, unidadeMedida, alvo,
				origem, matriculaMascarada, trechoId, trechoCodigo, logradouroId, logradouroNome, null, null, unidadeId,
				unidadeNome, campanhaId, campanhaNome, loteId, situacaoNaCriacao, prioridade, prazoEm, instrucoes,
				quantidadePrevista, equipeId, equipeNome, dataProgramada, enviadaEm, status, resultado, motivoId,
				motivoNome, observacao, quantidadeExecutada, resultadoEm, resultadoPor, movimentadaEm, movimentadaPor,
				conferencia, confirmadaEm, atrasada, criadaEm, criadaPor, latitude, longitude, sequenciaRota);
	}

}
