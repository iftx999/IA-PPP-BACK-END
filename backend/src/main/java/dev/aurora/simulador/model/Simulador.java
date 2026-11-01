package dev.aurora.simulador.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.contratos.RegraDePenalidade;
import org.jspecify.annotations.Nullable;

/**
 * Cálculo do simulador (ADR 0035), sem acesso a banco.
 * <ul>
 * <li>Ligações: ritmo constante a partir da competência medida, ligados = ⌊ligados₀ +
 * ritmo × meses⌋, nunca acima do total com rede.</li>
 * <li>Denominador: a rede do tamanho atual (como na projeção do painel, RN-05).</li>
 * <li>Meta: a aplicável hoje; depois do prazo ela continua sendo a referência da multa (a
 * obrigação vencida não some).</li>
 * <li>Aferição: no mês do prazo e, depois, a cada período da regra (mensal ou 12
 * meses).</li>
 * </ul>
 */
public final class Simulador {

	private static final BigDecimal CEM = BigDecimal.valueOf(100);

	private Simulador() {
	}

	public static Cenario projetar(BaseDaSimulacao base, BigDecimal ligacoesPorMes, YearMonth fim,
			List<RegraDePenalidade> regras) {
		var atual = base.atual();
		var comRede = atual.comRede();
		var meta = base.meta();
		var prazo = meta == null ? null : meta.prazo();
		var ritmo = ligacoesPorMes.max(BigDecimal.ZERO);

		var meses = new ArrayList<Cenario.MesProjetado>();
		var porRegra = new LinkedHashMap<UUID, BigDecimal>();
		var afericoesComMulta = new LinkedHashMap<UUID, Integer>();
		regras.forEach(r -> {
			porRegra.put(r.itemId(), BigDecimal.ZERO.setScale(2));
			afericoesComMulta.put(r.itemId(), 0);
		});
		YearMonth mesAtingimento = null;
		BigDecimal adesaoNoPrazo = null;
		var total = BigDecimal.ZERO.setScale(2);

		long quantos = Math.max(0, ChronoUnit.MONTHS.between(base.competencia(), fim));
		for (int i = 0; i <= quantos; i++) {
			var competencia = base.competencia().plusMonths(i);
			long ligados = i == 0 ? atual.ligados()
					: Math.min(comRede,
							BigDecimal.valueOf(atual.ligados())
								.add(ritmo.multiply(BigDecimal.valueOf(i)))
								.setScale(0, RoundingMode.FLOOR)
								.longValueExact());
			var adesao = BigDecimal.valueOf(ligados)
				.multiply(CEM)
				.divide(BigDecimal.valueOf(comRede), 4, RoundingMode.HALF_UP);
			var pontosAbaixo = meta == null ? BigDecimal.ZERO.setScale(4)
					: meta.valor().subtract(adesao).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
			if (meta != null && mesAtingimento == null && pontosAbaixo.signum() == 0) {
				mesAtingimento = competencia;
			}
			if (competencia.equals(prazo)) {
				adesaoNoPrazo = adesao;
			}

			boolean aferido = false;
			var multaDoMes = BigDecimal.ZERO.setScale(2);
			for (var regra : regras) {
				if (!aferidaEm(regra, prazo, competencia)) {
					continue;
				}
				aferido = true;
				var multa = regra.multa(pontosAbaixo);
				if (multa.signum() > 0) {
					multaDoMes = multaDoMes.add(multa);
					porRegra.merge(regra.itemId(), multa, BigDecimal::add);
					afericoesComMulta.merge(regra.itemId(), 1, Integer::sum);
				}
			}
			total = total.add(multaDoMes);
			meses
				.add(new Cenario.MesProjetado(competencia, ligados, adesao, i == 0, pontosAbaixo, aferido, multaDoMes));
		}

		var multas = porRegra.entrySet()
			.stream()
			.map(e -> new Cenario.MultaDaRegra(e.getKey(), e.getValue(), afericoesComMulta.get(e.getKey())))
			.toList();
		return new Cenario(ritmo, meses, mesAtingimento, adesaoNoPrazo, total, multas);
	}

	/**
	 * Ritmo constante (inteiro) que atinge a meta no mês do prazo; zero se já atingida;
	 * nulo sem meta ou sem prazo futuro.
	 */
	public static @Nullable BigDecimal ligacoesNecessariasPorMes(BaseDaSimulacao base) {
		var meta = base.meta();
		if (meta == null || meta.prazo() == null) {
			return null;
		}
		long mesesAtePrazo = ChronoUnit.MONTHS.between(base.competencia(), meta.prazo());
		var atual = base.atual();
		long necessarios = meta.valor()
			.multiply(BigDecimal.valueOf(atual.comRede()))
			.divide(CEM, 0, RoundingMode.CEILING)
			.longValue();
		long faltam = necessarios - atual.ligados();
		if (faltam <= 0) {
			return BigDecimal.ZERO;
		}
		if (mesesAtePrazo <= 0) {
			return null;
		}
		return BigDecimal.valueOf(faltam).divide(BigDecimal.valueOf(mesesAtePrazo), 0, RoundingMode.CEILING);
	}

	static boolean aferidaEm(RegraDePenalidade regra, @Nullable YearMonth prazo, YearMonth competencia) {
		if (prazo == null || competencia.isBefore(prazo)) {
			return false;
		}
		return ChronoUnit.MONTHS.between(prazo, competencia) % regra.periodicidade().meses() == 0;
	}

}
