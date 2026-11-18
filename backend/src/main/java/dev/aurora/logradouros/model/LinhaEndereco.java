package dev.aurora.logradouros.model;

import org.jspecify.annotations.Nullable;

/**
 * Linha do CSV de endereços (matricula;codigo_logradouro;numero;complemento), já
 * validada.
 */
public record LinhaEndereco(int linha, String matricula, String codigoLogradouro, @Nullable String numero,
		@Nullable Integer numeroInteiro, @Nullable String complemento) {
}
