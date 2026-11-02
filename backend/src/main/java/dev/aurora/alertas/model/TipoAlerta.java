package dev.aurora.alertas.model;

/** Tipos do schema herdado (RN-07, ADR 0024) e o de OS divergente (ADR 0036). */
public enum TipoAlerta {

	INDICADOR_ABAIXO_DA_META, META_FORA_DO_RITMO, FONTE_ATRASADA, PRAZO_CONTRATUAL, TRILHA_ALTERADA,

	/** ADR 0036: OS executada que a carga não confirmou (RN-19). */
	ORDEM_DIVERGENTE

}
