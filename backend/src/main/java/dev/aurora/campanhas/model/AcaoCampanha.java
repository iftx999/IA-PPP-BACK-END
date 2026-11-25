package dev.aurora.campanhas.model;

/**
 * O que a campanha faz em cada imóvel; define o serviço do catálogo das OS (ADR 0036,
 * 0044).
 */
public enum AcaoCampanha {

	/** Factível vira ligação nova; cortado vira religação. Conferidas pela carga. */
	LIGACAO,

	/** Visita técnica antes da ligação (viabilidade, cota, ramal). */
	VISTORIA,

	/** Notificação da obrigação de se ligar à rede. */
	NOTIFICACAO;

	/** Código do serviço do catálogo inicial (V31) para a situação do imóvel. */
	public String servicoPara(String situacaoImovel) {
		return switch (this) {
			case LIGACAO -> "CORTADO".equals(situacaoImovel) ? "RELIGACAO" : "LIGACAO_NOVA";
			case VISTORIA -> "VISTORIA";
			case NOTIFICACAO -> "NOTIFICACAO";
		};
	}

}
