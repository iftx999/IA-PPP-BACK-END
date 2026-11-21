package dev.aurora.operacao.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Indicadores da operação num período (J12), pelas OS programadas nele. Tempos medidos
 * pelo relógio do celular (RN-26); km pelas posições da jornada.
 */
public record Indicadores(LocalDate de, LocalDate ate, Resumo resumo, List<PrazoDoServico> prazos,
		List<TempoDoServico> tempos, List<ContagemPorMotivo> motivos, List<ProdutividadeDaEquipe> equipes) {

	public Indicadores {
		prazos = List.copyOf(prazos);
		tempos = List.copyOf(tempos);
		motivos = List.copyOf(motivos);
		equipes = List.copyOf(equipes);
	}

	/**
	 * @param noPrazo encerradas executadas até o prazo
	 * @param foraDoPrazo encerradas executadas depois do prazo
	 * @param vencidasAbertas ainda abertas com o prazo passado
	 */
	public record Resumo(int ordens, int executadas, int naoExecutadas, int devolvidas, int noPrazo, int foraDoPrazo,
			int vencidasAbertas, @Nullable Double deslocamentoMedioMin, @Nullable Double execucaoMediaMin, double km) {
	}

	public record PrazoDoServico(UUID servicoId, String servico, int noPrazo, int foraDoPrazo, int vencidasAbertas,
			int abertasNoPrazo) {
	}

	/**
	 * Tempo real de execução (sem as paralisações) × tempo padrão do serviço.
	 *
	 * @param razaoMediana mediana de real ÷ padrão por OS
	 * @param tempoSugerido novo tempo padrão sugerido (por OS ou por unidade de medida),
	 * quando há amostras suficientes e a diferença passa da tolerância
	 */
	public record TempoDoServico(UUID servicoId, String servico, String unidadeMedida, boolean tempoPorUnidade,
			int amostras, double execucaoMediaMin, double padraoMedioMin, @Nullable Double razaoMediana,
			double tempoPadraoAtual, @Nullable Double tempoSugerido) {

		public TempoDoServico comSugestao(@Nullable Double sugerido) {
			return new TempoDoServico(servicoId, servico, unidadeMedida, tempoPorUnidade, amostras, execucaoMediaMin,
					padraoMedioMin, razaoMediana, tempoPadraoAtual, sugerido);
		}

	}

	/** Paralisações, devoluções e não execuções por motivo. */
	public record ContagemPorMotivo(String tipo, String codigo, String nome, int quantidade) {
	}

	public record ProdutividadeDaEquipe(UUID equipeId, String equipe, int executadas, int naoExecutadas, int devolvidas,
			double horasDeJornada, @Nullable Double osPorHora, double km, @Nullable Double kmPorOs,
			@Nullable Double deslocamentoMedioMin) {
	}

}
