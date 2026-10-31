package dev.aurora.contratos;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Data a cumprir de um contrato: item PRAZO aprovado e ainda não cumprido, ou o fim da
 * vigência do próprio contrato ({@code itemId} nulo).
 */
public record PrazoContratual(UUID contratoId, String contratoNumero, @Nullable UUID itemId, String titulo,
		@Nullable String clausula, UUID unidadeId, LocalDate data) {

	public boolean fimDoContrato() {
		return itemId == null;
	}

}
