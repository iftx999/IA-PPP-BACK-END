package dev.aurora.roteirizacao.otimizacao;

import java.util.List;

/**
 * Horários previstos de uma rota, em segundos desde a meia-noite do dia.
 *
 * @param fimSeg saída da última parada (ou o início, sem paradas)
 */
public record LinhaDoTempo(List<Item> itens, long inicioSeg, long fimSeg, long deslocamentoSeg, long metros) {

	/**
	 * @param viagemSeg deslocamento desde a parada anterior (ou a partida)
	 */
	public record Item(Parada parada, long chegadaSeg, long saidaSeg, int viagemSeg, int metros) {
	}

}
