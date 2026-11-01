package dev.aurora.simulador.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.contratos.RegraDePenalidade;
import org.jspecify.annotations.Nullable;

/**
 * Resultado do simulador: o ritmo atual comparado ao cenário pedido, sobre a mesma base e
 * as mesmas regras.
 *
 * @param ligacoesNecessariasPorMes ritmo constante que atinge a meta no prazo; nulo sem
 * prazo futuro
 * @param fim último mês projetado
 * @param unidadesComRegra onde há regras visíveis, na ordem da árvore
 * @param regrasVisiveis falso quando a pessoa não pode consultar contratos na unidade (a
 * multa não é estimada)
 */
public record Simulacao(BaseDaSimulacao base, List<RegraDePenalidade> regras, boolean regrasVisiveis,
		List<UnidadeComRegra> unidadesComRegra, @Nullable BigDecimal ligacoesNecessariasPorMes, YearMonth fim,
		Cenario ritmoAtual, Cenario cenario) {

	/** Unidade onde alguma penalidade calculável é aferida (atalho na tela). */
	public record UnidadeComRegra(UUID id, String nome, int regras) {
	}

}
