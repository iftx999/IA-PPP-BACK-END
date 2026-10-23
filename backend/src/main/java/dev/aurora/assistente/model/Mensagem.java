package dev.aurora.assistente.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * @param numerosSemFonte números da resposta que não aparecem em nenhuma fonte (G6): a
 * tela avisa
 * @param avaliacao 1 = ajudou, -1 = tem erro (G4)
 */
public record Mensagem(UUID id, Papel papel, String texto, List<Fonte> fontes, List<LinhaDeDados> dados,
		List<String> numerosSemFonte, @Nullable Integer avaliacao, Instant criadaEm) {
}
