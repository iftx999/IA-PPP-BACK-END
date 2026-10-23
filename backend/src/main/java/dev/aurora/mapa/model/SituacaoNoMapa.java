package dev.aurora.mapa.model;

/**
 * Situação do imóvel como aparece no mapa (E2). {@code SEM_REDE} é a situação
 * {@code POTENCIAL} do sistema comercial: ainda não há rede na frente do imóvel.
 */
public enum SituacaoNoMapa {

	LIGADO("LIGADO", "Ligado"), FACTIVEL("FACTIVEL", "Factível sem ligação"), CORTADO("CORTADO", "Cortado"),
	SEM_REDE("POTENCIAL", "Sem rede");

	private final String noBanco;

	private final String rotulo;

	SituacaoNoMapa(String noBanco, String rotulo) {
		this.noBanco = noBanco;
		this.rotulo = rotulo;
	}

	public String noBanco() {
		return noBanco;
	}

	public String rotulo() {
		return rotulo;
	}

	public static SituacaoNoMapa doBanco(String valor) {
		for (var situacao : values()) {
			if (situacao.noBanco.equals(valor)) {
				return situacao;
			}
		}
		throw new IllegalArgumentException(valor);
	}

}
