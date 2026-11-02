package dev.aurora.campanhas.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoOrdem;
import org.jspecify.annotations.Nullable;

/**
 * Ordem de serviço (I3). Matrícula mascarada sem {@code dados_pessoais:ler}.
 *
 * @param situacaoNaCriacao FACTIVEL ou CORTADO
 * @param atrasada em aberto depois do prazo (ou da data reagendada)
 * @param confirmadaEm competência da carga que mostrou a ligação
 */
record OrdemServicoResponse(UUID id, UUID campanhaId, String campanhaNome, int numero, String matricula, UUID unidadeId,
		String unidadeNome, TipoOrdem tipo, String situacaoNaCriacao, Prioridade prioridade, LocalDate prazo,
		@Nullable UUID equipeId, @Nullable String equipeNome, @Nullable Instant enviadaEm, StatusOrdem status,
		@Nullable MotivoResultado motivo, @Nullable String observacao, @Nullable LocalDate reagendadaPara,
		@Nullable Instant resultadoEm, @Nullable String resultadoPor, Conferencia conferencia,
		@Nullable YearMonth confirmadaEm, boolean atrasada, @Nullable Double latitude, @Nullable Double longitude) {

	static OrdemServicoResponse de(OrdemServico o) {
		return new OrdemServicoResponse(o.id(), o.campanhaId(), o.campanhaNome(), o.numero(), o.matricula(),
				o.unidadeId(), o.unidadeNome(), o.tipo(), o.situacaoNaCriacao(), o.prioridade(), o.prazo(),
				o.equipeId(), o.equipeNome(), o.enviadaEm(), o.status(), o.motivo(), o.observacao(), o.reagendadaPara(),
				o.resultadoEm(), o.resultadoPor(), o.conferencia(), o.confirmadaEm(), o.atrasada(), o.latitude(),
				o.longitude());
	}

}
