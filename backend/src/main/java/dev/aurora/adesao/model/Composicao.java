package dev.aurora.adesao.model;

/** Imóveis sem ligação por situação (RN-02): factíveis (nunca ligados) e cortados. */
public record Composicao(long factiveis, long cortados) {

	public long semLigacao() {
		return factiveis + cortados;
	}

}
