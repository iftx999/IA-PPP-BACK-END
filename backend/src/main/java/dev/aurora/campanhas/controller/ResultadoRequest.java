package dev.aurora.campanhas.controller;

import java.math.BigDecimal;
import java.util.UUID;

import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.ResultadoOrdem;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Encerramento da OS (doc 10, 10.6).
 *
 * @param motivoId de não execução; obrigatório em não executada
 * @param quantidadeExecutada só na executada; omitida = a prevista
 */
record ResultadoRequest(@NotNull ResultadoOrdem resultado, @Nullable UUID motivoId,
		@Nullable @Size(max = 500) String observacao,
		@Nullable @Digits(integer = 10, fraction = 2) BigDecimal quantidadeExecutada) {

	RegistroResultado registro() {
		return new RegistroResultado(resultado, motivoId, observacao, quantidadeExecutada);
	}

}
