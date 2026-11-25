package dev.aurora.campanhas.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Resposta do servidor a um evento (10.9.4).
 *
 * @param original na resposta REPETIDO, o que aconteceu da primeira vez
 * @param avisos RELOGIO, FORA_DO_LOCAL, SEM_GPS, SEM_JORNADA, EXECUCAO_SIMULTANEA,
 * APARELHO_REVOGADO
 */
public record RespostaDoEvento(UUID id, SituacaoEvento situacao, @Nullable SituacaoEvento original,
		@Nullable String mensagem, List<String> avisos) {

	public RespostaDoEvento {
		avisos = List.copyOf(avisos);
	}

}
