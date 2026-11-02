package dev.aurora.campanhas.controller;

import java.time.LocalDate;

import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.ResultadoOrdem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Resultado de campo (I3).
 *
 * @param motivo obrigatório em recusada e inviável
 * @param reagendadaPara obrigatória em reagendada
 */
record ResultadoRequest(@NotNull ResultadoOrdem resultado, @Nullable MotivoResultado motivo,
		@Nullable @Size(max = 500) String observacao, @Nullable LocalDate reagendadaPara) {

	RegistroResultado registro() {
		return new RegistroResultado(resultado, motivo, observacao, reagendadaPara);
	}

}
