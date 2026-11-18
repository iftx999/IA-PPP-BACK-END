package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Ação registrada no celular (10.9.3), enviada pela fila.
 *
 * @param id gerado no celular (idempotência)
 * @param sequencia ordem dos eventos da OS no celular, a partir de 1
 * @param resultado só no encerramento
 * @param valores parâmetros do serviço no encerramento executado (chave → valor; fotos
 * pelos ids dos anexos)
 * @param ocorridoEm relógio do celular
 * @param versaoPacote versão do pacote do dia que o celular tinha
 */
public record EventoDeCampo(UUID id, UUID osId, int sequencia, AcaoDeCampo tipo, @Nullable UUID motivoId,
		@Nullable String observacao, @Nullable ResultadoOrdem resultado, @Nullable BigDecimal quantidade,
		@Nullable JsonNode valores, Instant ocorridoEm, @Nullable Posicao posicao, @Nullable Long versaoPacote) {
}
