package dev.aurora.contratos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Regra calculável de uma penalidade aprovada (ADR 0035). A multa é aferida na unidade do
 * item (ou, sem ela, na do contrato) contra a meta de adesão aplicável ali.
 *
 * @param baseValor base em reais da fórmula percentual (ex.: faturamento mensal); nula
 * nas demais
 * @param teto limite em reais por aferição; nulo = sem limite
 */
public record RegraDePenalidade(UUID itemId, UUID contratoId, String contratoNumero, String titulo,
		@Nullable String clausula, int pagina, UUID unidadeId, String unidadeNome, FormulaPenalidade formula,
		BigDecimal valor, @Nullable BigDecimal baseValor, @Nullable String baseDescricao, ContagemPontos contagem,
		PeriodicidadePenalidade periodicidade, @Nullable BigDecimal teto, Instant atualizadoEm,
		@Nullable String atualizadoPor) {

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	/**
	 * Multa estimada de uma aferição, em reais com 2 casas.
	 * @param pontosAbaixo pontos percentuais abaixo da meta; zero ou negativo = meta
	 * atingida, sem multa
	 */
	public BigDecimal multa(BigDecimal pontosAbaixo) {
		if (pontosAbaixo.signum() <= 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		var pontos = contagem == ContagemPontos.POR_PONTO_OU_FRACAO ? pontosAbaixo.setScale(0, RoundingMode.CEILING)
				: pontosAbaixo;
		var multa = switch (formula) {
			case PERCENTUAL_DA_BASE_POR_PONTO -> exigirBase().multiply(valor).divide(CEM).multiply(pontos);
			case VALOR_POR_PONTO -> valor.multiply(pontos);
			case VALOR_FIXO -> valor;
		};
		if (teto != null) {
			multa = multa.min(teto);
		}
		return multa.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal exigirBase() {
		if (baseValor == null) {
			throw new IllegalStateException("Fórmula percentual sem base em reais.");
		}
		return baseValor;
	}

}
