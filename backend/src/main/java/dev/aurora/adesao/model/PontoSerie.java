package dev.aurora.adesao.model;

import java.time.YearMonth;

/** Adesão de uma unidade em uma competência (mês). */
public record PontoSerie(YearMonth competencia, Adesao adesao) {

	public long ligados() {
		return adesao.ligados();
	}

	public long comRede() {
		return adesao.comRede();
	}

}
