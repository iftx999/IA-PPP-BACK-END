package dev.aurora.roteirizacao.model;

/** Como os deslocamentos foram calculados (roteirizacao.calculo). */
public enum Calculo {

	/** Pela malha viária (Valhalla). */
	VALHALLA,
	/** Em linha reta com fator de correção: serviço de rotas desligado ou fora do ar. */
	LINHA_RETA

}
