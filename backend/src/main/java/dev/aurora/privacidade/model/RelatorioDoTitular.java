package dev.aurora.privacidade.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Tudo o que a Aurora guarda de uma matrícula no escopo de quem consulta (ADR 0041). Sem
 * nome nem endereço: a Aurora não os recebe (RN-09).
 */
public record RelatorioDoTitular(String matricula, String protocolo, Instant emitidoEm, List<SituacaoMensal> situacoes,
		@Nullable Localizacao localizacao, List<OrdemDeServico> ordens) {

	public boolean encontrado() {
		return !situacoes.isEmpty() || localizacao != null || !ordens.isEmpty();
	}

	/** Situação do imóvel numa competência, como veio do sistema comercial. */
	public record SituacaoMensal(YearMonth competencia, String situacao, @Nullable String motivoCorte, UUID unidadeId,
			String unidadeNome, String fonte, Instant atualizadoEm) {
	}

	/** Ponto do imóvel no mapa (sem endereço). */
	public record Localizacao(double longitude, double latitude, String origem, String precisao, UUID unidadeId,
			String unidadeNome, Instant atualizadoEm) {
	}

	/** Ordem de serviço aberta para o imóvel (ADR 0036), com o que a equipe registrou. */
	public record OrdemDeServico(UUID id, int numero, String campanha, String tipo, String status, LocalDate prazo,
			@Nullable String equipe, @Nullable String motivo, @Nullable String observacao,
			@Nullable Instant resultadoEm, Instant criadaEm) {
	}

}
