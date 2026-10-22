package dev.aurora.contratos.model;

/** Etapas do processamento de um PDF (nomes do schema herdado). */
public enum SituacaoDocumento {

	RECEBIDO, RECONHECENDO_TEXTO, PROCESSANDO, PRONTO,

	/** PDF sem camada de texto (digitalizado): precisa de OCR. */
	SEM_TEXTO,

	FALHA;

	public boolean emAndamento() {
		return this == RECEBIDO || this == RECONHECENDO_TEXTO || this == PROCESSANDO;
	}

}
