package dev.aurora.adesao.model;

import java.math.BigDecimal;

/** RN-03: semáforo da unidade a partir da adesão e da meta aplicável. */
public enum Situacao {

	META_ATINGIDA, ATENCAO, ABAIXO_DO_ALERTA, SEM_META;

	public static Situacao classificar(BigDecimal adesao, MetaVigente meta) {
		if (meta == null || adesao == null) {
			return SEM_META;
		}
		if (adesao.compareTo(meta.valorMeta()) >= 0) {
			return META_ATINGIDA;
		}
		if (meta.valorAlerta() != null && adesao.compareTo(meta.valorAlerta()) < 0) {
			return ABAIXO_DO_ALERTA;
		}
		return ATENCAO;
	}

}
