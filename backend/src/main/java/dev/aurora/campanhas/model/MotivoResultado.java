package dev.aurora.campanhas.model;

/**
 * Motivo de OS recusada pelo morador ou inviável tecnicamente (I3). {@link #OUTRO} exige
 * observação.
 */
public enum MotivoResultado {

	SEM_INTERESSE(true), CUSTO_DA_LIGACAO(true), SOLUCAO_PROPRIA(true), DEBITO_PENDENTE(true),

	SEM_REDE_NA_RUA(false), IMOVEL_ABAIXO_DA_REDE(false), IMOVEL_DESOCUPADO(false),

	OUTRO(true);

	private final boolean deRecusa;

	MotivoResultado(boolean deRecusa) {
		this.deRecusa = deRecusa;
	}

	/** O motivo combina com o resultado (recusa do morador ou inviabilidade técnica). */
	public boolean valePara(ResultadoOrdem resultado) {
		return switch (resultado) {
			case RECUSADA -> deRecusa;
			case INVIAVEL -> !deRecusa || this == OUTRO;
			default -> false;
		};
	}

}
