package dev.aurora.adesao.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * RN-05: projeção "no ritmo".
 * <ul>
 * <li>Ritmo atual: média mensal de novas ligações nos últimos {@value #JANELA_MESES} meses, usando só meses com o
 * tamanho de rede atual. Quando a rede muda, o mês da mudança e os anteriores saem da conta, porque comparam outro
 * denominador (ex.: rede cresceu em abr/2026 → base mai–ago = (8.761 − 8.357) / 4 = 101/mês).</li>
 * <li>Denominador futuro: a rede do tamanho atual (não prevê expansão).</li>
 * <li>Faltam = ⌈meta% × com rede − ligados⌉; ritmo necessário = faltam / meses até o fim da vigência.</li>
 * <li>Projeção na data = (ligados + ritmo × meses restantes) / com rede.</li>
 * </ul>
 * Campos que dependem de prazo ficam nulos quando a meta não tem fim; {@code mesAtingimento} fica nulo quando o
 * ritmo atual não é positivo.
 */
public record Projecao(BigDecimal ritmoAtual, int mesesNaBase, long faltam, Integer mesesRestantes,
		BigDecimal ritmoNecessario, BigDecimal projecaoNoPrazo, YearMonth prazo, YearMonth mesAtingimento,
		boolean atinge) {

	public static final int JANELA_MESES = 6;

	/**
	 * @param serie pontos em ordem cronológica, terminando na competência atual
	 * @param metaPct meta em percentual (ex.: 80)
	 * @param vigenciaFim fim da vigência da meta; nulo = "sem fim"
	 */
	public static Optional<Projecao> calcular(List<PontoSerie> serie, BigDecimal metaPct, LocalDate vigenciaFim) {
		if (serie.isEmpty() || metaPct == null) {
			return Optional.empty();
		}
		var atual = serie.getLast();
		if (atual.comRede() == 0) {
			return Optional.empty();
		}

		// Recua até 6 meses enquanto o tamanho da rede for o atual.
		var base = atual;
		int meses = 0;
		for (int i = serie.size() - 2; i >= 0 && meses < JANELA_MESES; i--) {
			var anterior = serie.get(i);
			if (anterior.comRede() != atual.comRede()
					|| !anterior.competencia().equals(base.competencia().minusMonths(1))) {
				break;
			}
			base = anterior;
			meses++;
		}
		var ritmo = meses == 0 ? null
				: BigDecimal.valueOf(atual.ligados() - base.ligados())
					.divide(BigDecimal.valueOf(meses), 1, RoundingMode.HALF_UP);

		var necessarioTotal = metaPct.multiply(BigDecimal.valueOf(atual.comRede()))
			.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
			.longValue();
		long faltam = Math.max(0, necessarioTotal - atual.ligados());

		YearMonth prazo = vigenciaFim == null ? null : YearMonth.from(vigenciaFim);
		Integer mesesRestantes = prazo == null ? null
				: (int) Math.max(0, ChronoUnit.MONTHS.between(atual.competencia(), prazo));

		BigDecimal ritmoNecessario = null;
		BigDecimal projecaoNoPrazo = null;
		if (mesesRestantes != null) {
			ritmoNecessario = mesesRestantes == 0 ? null
					: BigDecimal.valueOf(faltam).divide(BigDecimal.valueOf(mesesRestantes), 1, RoundingMode.HALF_UP);
			var ritmoProjetado = ritmo == null ? BigDecimal.ZERO : ritmo.max(BigDecimal.ZERO);
			var ligadosNoPrazo = BigDecimal.valueOf(atual.ligados())
				.add(ritmoProjetado.multiply(BigDecimal.valueOf(mesesRestantes)))
				.min(BigDecimal.valueOf(atual.comRede()));
			projecaoNoPrazo = ligadosNoPrazo.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(atual.comRede()), 4, RoundingMode.HALF_UP);
		}

		YearMonth mesAtingimento;
		if (faltam == 0) {
			mesAtingimento = atual.competencia();
		}
		else if (ritmo != null && ritmo.signum() > 0) {
			long mesesAteAtingir = BigDecimal.valueOf(faltam).divide(ritmo, 0, RoundingMode.CEILING).longValue();
			mesAtingimento = atual.competencia().plusMonths(mesesAteAtingir);
		}
		else {
			mesAtingimento = null;
		}

		boolean atinge = faltam == 0 || (prazo == null ? mesAtingimento != null
				: projecaoNoPrazo.compareTo(metaPct) >= 0);

		return Optional.of(new Projecao(ritmo, meses, faltam, mesesRestantes, ritmoNecessario, projecaoNoPrazo, prazo,
				mesAtingimento, atinge));
	}

}
