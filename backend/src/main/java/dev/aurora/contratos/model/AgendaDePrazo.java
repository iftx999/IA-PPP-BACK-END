package dev.aurora.contratos.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.aurora.contratos.Recorrencia;
import org.jspecify.annotations.Nullable;

/**
 * Agenda de um prazo ou obrigação aprovado (ADR 0026): primeiro vencimento, repetição e
 * vencimentos já cumpridos. Os vencimentos são cumpridos em ordem; o próximo é o primeiro
 * depois do último cumprido. Repetições não passam do fim da vigência do contrato.
 *
 * @param fimDoContrato fim da vigência do contrato; nulo se indeterminado
 */
public record AgendaDePrazo(LocalDate primeiroVencimento, @Nullable Recorrencia recorrencia,
		List<Cumprimento> cumprimentos, @Nullable LocalDate fimDoContrato) {

	/** Limite de segurança para agendas mensais muito longas. */
	private static final int MAXIMO_OCORRENCIAS = 1200;

	public record Cumprimento(LocalDate vencimento, Instant cumpridoEm, @Nullable String cumpridoPor) {
	}

	public AgendaDePrazo {
		cumprimentos = cumprimentos.stream().sorted(Comparator.comparing(Cumprimento::vencimento)).toList();
	}

	/** Vencimento a cumprir agora; vazio quando tudo foi cumprido. */
	public Optional<LocalDate> proximoVencimento() {
		var ultimo = cumprimentos.isEmpty() ? null : cumprimentos.getLast().vencimento();
		for (var k = 0; k < MAXIMO_OCORRENCIAS; k++) {
			if (k > 0 && recorrencia == null) {
				return Optional.empty();
			}
			var vencimento = recorrencia == null ? primeiroVencimento
					: primeiroVencimento.plusMonths((long) k * recorrencia.meses());
			if (k > 0 && fimDoContrato != null && vencimento.isAfter(fimDoContrato)) {
				return Optional.empty();
			}
			if (ultimo == null || vencimento.isAfter(ultimo)) {
				return Optional.of(vencimento);
			}
		}
		return Optional.empty();
	}

	public Optional<Cumprimento> ultimoCumprimento() {
		return cumprimentos.isEmpty() ? Optional.empty() : Optional.of(cumprimentos.getLast());
	}

}
