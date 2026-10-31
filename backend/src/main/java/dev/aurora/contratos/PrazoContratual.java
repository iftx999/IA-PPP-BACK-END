package dev.aurora.contratos;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Data a cumprir de um contrato: o próximo vencimento de um prazo ou obrigação aprovado
 * (ADR 0024, ADR 0026), ou o fim da vigência do próprio contrato ({@code itemId} nulo).
 *
 * @param recorrencia repetição do item; nula para vencimento único e fim de contrato
 */
public record PrazoContratual(UUID contratoId, String contratoNumero, @Nullable UUID itemId, String titulo,
		@Nullable String clausula, UUID unidadeId, LocalDate data, @Nullable Recorrencia recorrencia) {

	public boolean fimDoContrato() {
		return itemId == null;
	}

}
