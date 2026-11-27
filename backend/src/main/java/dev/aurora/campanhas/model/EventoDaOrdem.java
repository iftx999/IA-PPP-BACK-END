package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Evento do app no histórico da OS (J9, J11).
 *
 * @param fotos ids das fotos tiradas na ação (ADR 0053)
 */
public record EventoDaOrdem(UUID id, int sequencia, AcaoDeCampo tipo, SituacaoEvento situacao,
		@Nullable String mensagem, @Nullable ResultadoOrdem resultado, @Nullable String motivoNome,
		@Nullable String observacao, @Nullable BigDecimal quantidade, @Nullable JsonNode valores, Instant ocorridoEm,
		Instant recebidoEm, @Nullable Double latitude, @Nullable Double longitude, @Nullable BigDecimal precisaoM,
		@Nullable BigDecimal distanciaM, List<String> avisos, List<UUID> fotos, String equipeNome, String usuarioNome) {

	public EventoDaOrdem {
		avisos = List.copyOf(avisos);
		fotos = List.copyOf(fotos);
	}

}
