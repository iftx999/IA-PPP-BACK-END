package dev.aurora.simulador.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Um cenário projetado: um ritmo constante de novas ligações a partir da competência
 * atual.
 *
 * @param meses o mês 0 é a competência medida; os seguintes são projeção
 * @param mesAtingimento primeiro mês com a meta atingida; nulo se não sai no horizonte
 * @param adesaoNoPrazo adesão projetada no mês do prazo; nula sem prazo ou com prazo já
 * passado
 */
public record Cenario(BigDecimal ligacoesPorMes, List<MesProjetado> meses, @Nullable YearMonth mesAtingimento,
		@Nullable BigDecimal adesaoNoPrazo, BigDecimal multaTotal, List<MultaDaRegra> multasPorRegra) {

	/**
	 * @param pontosAbaixo pontos percentuais abaixo da meta (zero quando atingida)
	 * @param aferido o mês é de aferição das penalidades
	 */
	public record MesProjetado(YearMonth competencia, long ligados, BigDecimal adesao, boolean medido,
			BigDecimal pontosAbaixo, boolean aferido, BigDecimal multa) {
	}

	public record MultaDaRegra(UUID itemId, BigDecimal total, int afericoesComMulta) {
	}

}
