package dev.aurora.campanhas.controller;

import java.util.UUID;

import dev.aurora.campanhas.model.Justificativa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Motivo do catálogo e observação de uma devolução ou de um cancelamento (RN-22). */
record JustificativaRequest(@NotNull UUID motivoId, @Nullable @Size(max = 500) String observacao) {

	Justificativa justificativa() {
		return new Justificativa(motivoId, observacao);
	}

}
