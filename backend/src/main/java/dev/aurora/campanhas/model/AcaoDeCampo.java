package dev.aurora.campanhas.model;

import java.util.Optional;

/**
 * Ação registrada no app de campo (doc 10, 10.6; ADR 0045). Cada uma leva a OS de uma
 * situação a outra; devolver é a única que não é trabalho feito no local.
 */
public enum AcaoDeCampo {

	INICIAR, PARALISAR, RETOMAR, DEVOLVER, ENCERRAR;

	/** A situação depois da ação, se ela é permitida a partir de {@code atual}. */
	public Optional<StatusOrdem> destino(StatusOrdem atual) {
		var permitida = switch (this) {
			case INICIAR -> atual == StatusOrdem.PROGRAMADA;
			case PARALISAR, ENCERRAR -> atual == StatusOrdem.EM_EXECUCAO;
			case RETOMAR -> atual == StatusOrdem.PARALISADA;
			case DEVOLVER -> atual.devolvivel();
		};
		return permitida ? Optional.of(apos()) : Optional.empty();
	}

	private StatusOrdem apos() {
		return switch (this) {
			case INICIAR, RETOMAR -> StatusOrdem.EM_EXECUCAO;
			case PARALISAR -> StatusOrdem.PARALISADA;
			case DEVOLVER -> StatusOrdem.DEVOLVIDA;
			case ENCERRAR -> StatusOrdem.ENCERRADA;
		};
	}

	/**
	 * Trabalho feito no local (10.9.5): quando a programação mudou sem o agente saber,
	 * vale o que o campo fez.
	 */
	public boolean trabalhoDeCampo() {
		return this != DEVOLVER;
	}

	/** Verbo para as mensagens ("iniciar", "paralisar"...). */
	public String verbo() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

}
