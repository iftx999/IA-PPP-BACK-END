package dev.aurora.campanhas.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import dev.aurora.campanhas.model.AlvoOrdem;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.OrigemOrdem;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.StatusOrdem;
import org.jspecify.annotations.Nullable;

/**
 * Ordem de serviço (ADR 0036, 0044). Sem {@code dados_pessoais:ler}: matrícula mascarada,
 * sem número e complemento.
 *
 * @param matricula nula nas OS de trecho
 * @param situacaoNaCriacao situação do imóvel quando a OS nasceu (campanha e avulsa)
 * @param observacao do último movimento (encerramento, devolução, cancelamento)
 * @param instrucoes de quem criou a OS
 * @param atrasada em aberto depois do prazo (RN-21)
 * @param confirmadaEm competência da carga que mostrou a ligação (RN-19)
 * @param latitude ponto do imóvel ou meio do trecho
 */
record OrdemServicoResponse(UUID id, long numero, UUID servicoId, String servicoCodigo, String servicoNome,
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
		@Nullable String criadaPor, @Nullable Double latitude, @Nullable Double longitude) {

	static OrdemServicoResponse de(OrdemServico o) {
		return new OrdemServicoResponse(o.id(), o.numero(), o.servicoId(), o.servicoCodigo(), o.servicoNome(),
				o.servicoVersao(), o.unidadeMedida(), o.alvo(), o.origem(), o.matricula(), o.trechoId(),
				o.trechoCodigo(), o.logradouroId(), o.logradouroNome(), o.numeroImovel(), o.complemento(),
				o.unidadeId(), o.unidadeNome(), o.campanhaId(), o.campanhaNome(), o.loteId(), o.situacaoNaCriacao(),
				o.prioridade(), o.prazoEm(), o.instrucoes(), o.quantidadePrevista(), o.equipeId(), o.equipeNome(),
				o.dataProgramada(), o.enviadaEm(), o.status(), o.resultado(), o.motivoId(), o.motivoNome(),
				o.observacao(), o.quantidadeExecutada(), o.resultadoEm(), o.resultadoPor(), o.movimentadaEm(),
				o.movimentadaPor(), o.conferencia(), o.confirmadaEm(), o.atrasada(), o.criadaEm(), o.criadaPor(),
				o.latitude(), o.longitude());
	}

}
