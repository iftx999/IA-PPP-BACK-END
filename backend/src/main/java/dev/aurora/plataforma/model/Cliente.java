package dev.aurora.plataforma.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Cliente (tenant) visto pela administração da plataforma: cadastro, limites e situação
 * do provisionamento. Nada de dentro do cliente (usuários, contratos, imóveis).
 *
 * @param emissor realm do cliente no provedor de identidade
 * @param provisionadoEm nulo enquanto o provisionamento não terminou
 */
public record Cliente(UUID id, String codigo, String nome, boolean ativo, Instant criadoEm, @Nullable String emissor,
		@Nullable String contatoNome, @Nullable String contatoEmail, @Nullable Integer limiteUsuarios,
		@Nullable Long assistenteTokensPorMes, @Nullable BigDecimal assistenteUsdPorMes, boolean temLogo,
		@Nullable Instant provisionadoEm) {
}
