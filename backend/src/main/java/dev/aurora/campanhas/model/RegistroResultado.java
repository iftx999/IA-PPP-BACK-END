package dev.aurora.campanhas.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Encerramento informado pela equipe ou pelo gestor (doc 10, 10.6).
 *
 * @param motivoId de não execução; obrigatório em não executada
 * @param quantidadeExecutada nula: a prevista na executada, zero na não executada
 */
public record RegistroResultado(ResultadoOrdem resultado, @Nullable UUID motivoId, @Nullable String observacao,
		@Nullable BigDecimal quantidadeExecutada) {
}
