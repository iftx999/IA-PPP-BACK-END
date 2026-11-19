package dev.aurora.roteirizacao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * OS como a roteirização usa.
 *
 * @param entrada onde a equipe chega (imóvel ou início do trecho)
 * @param saida de onde sai (o mesmo ponto, ou o fim do trecho)
 * @param duracaoSeg tempo padrão do serviço (× quantidade prevista quando é por unidade)
 */
public record OrdemParaRota(UUID id, long numero, String servicoNome, String status, String prioridade,
		@Nullable Instant prazo, @Nullable Coordenada entrada, @Nullable Coordenada saida, int duracaoSeg,
		@Nullable UUID equipeId, @Nullable LocalDate dataProgramada) {

	public boolean emCampo() {
		return "EM_EXECUCAO".equals(status) || "PARALISADA".equals(status);
	}

}
