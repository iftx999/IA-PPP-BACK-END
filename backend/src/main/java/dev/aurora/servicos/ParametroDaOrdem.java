package dev.aurora.servicos;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Parâmetro de encerramento de uma versão do serviço (J2), como o app e a validação do
 * encerramento o usam (RN-22).
 *
 * @param tipo INTEIRO, DECIMAL, TEXTO, LISTA, LISTA_MULTIPLA, SIM_NAO, DATA_HORA ou FOTO
 * @param minimo valor (número), caracteres (texto) ou fotos (foto)
 * @param condicaoChave só vale quando o parâmetro desta chave tem {@code condicaoValor}
 */
public record ParametroDaOrdem(String chave, String rotulo, @Nullable String ajuda, String tipo, boolean obrigatorio,
		@Nullable BigDecimal minimo, @Nullable BigDecimal maximo, @Nullable String unidade, List<String> opcoes,
		@Nullable String condicaoChave, @Nullable String condicaoValor) {

	public ParametroDaOrdem {
		opcoes = List.copyOf(opcoes);
	}

}
