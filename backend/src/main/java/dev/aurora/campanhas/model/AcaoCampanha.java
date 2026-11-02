package dev.aurora.campanhas.model;

/** O que a campanha faz em cada imóvel; define o tipo das OS (ADR 0036). */
public enum AcaoCampanha {

	/** Factível vira ligação nova; cortado vira religação. Conferidas pela carga. */
	LIGACAO,

	/** Visita técnica antes da ligação (viabilidade, cota, ramal). */
	VISTORIA,

	/** Notificação da obrigação de se ligar à rede. */
	NOTIFICACAO;

	public TipoOrdem tipoPara(String situacaoImovel) {
		return switch (this) {
			case LIGACAO -> "CORTADO".equals(situacaoImovel) ? TipoOrdem.RELIGACAO : TipoOrdem.LIGACAO_NOVA;
			case VISTORIA -> TipoOrdem.VISTORIA;
			case NOTIFICACAO -> TipoOrdem.NOTIFICACAO;
		};
	}

}
