package dev.aurora.adesao;

import java.math.BigDecimal;

import dev.aurora.adesao.model.MetaVigente;
import org.jspecify.annotations.Nullable;

/** RN-03: semáforo da unidade a partir da adesão e da meta aplicável. */
public enum Situacao {

	META_ATINGIDA, ATENCAO, ABAIXO_DO_ALERTA, SEM_META;

	public static Situacao classificar(@Nullable BigDecimal adesao, @Nullable MetaVigente meta) {
		return meta == null ? SEM_META : classificar(adesao, meta.valorMeta(), meta.valorAlerta());
	}

	/**
	 * Adesão ≥ meta: atingida; abaixo do limite de alerta: crítico; entre os dois:
	 * atenção.
	 */
	public static Situacao classificar(@Nullable BigDecimal adesao, @Nullable BigDecimal valorMeta,
			@Nullable BigDecimal valorAlerta) {
		if (adesao == null || valorMeta == null) {
			return SEM_META;
		}
		if (adesao.compareTo(valorMeta) >= 0) {
			return META_ATINGIDA;
		}
		if (valorAlerta != null && adesao.compareTo(valorAlerta) < 0) {
			return ABAIXO_DO_ALERTA;
		}
		return ATENCAO;
	}

}
