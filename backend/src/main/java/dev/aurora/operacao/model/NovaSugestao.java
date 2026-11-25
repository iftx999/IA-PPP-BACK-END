package dev.aurora.operacao.model;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Um fato que pede reotimização, como o detector o encontrou.
 *
 * @param chave identifica o fato: o mesmo fato não abre duas sugestões
 */
public record NovaSugestao(UUID unidadeId, LocalDate data, Gatilho gatilho, @Nullable UUID equipeId,
		@Nullable UUID osId, String chave, String descricao, @Nullable Integer atrasoMin) {
}
