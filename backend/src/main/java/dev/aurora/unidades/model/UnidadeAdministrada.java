package dev.aurora.unidades.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Unidade como a administração a vê: inclui inativas e o que a prende (filhas ativas e
 * concessões de acesso vigentes).
 */
public record UnidadeAdministrada(UUID id, TipoUnidade tipo, @Nullable UUID unidadePaiId, String codigo, String nome,
		@Nullable String codigoIbge, @Nullable BigDecimal latitude, @Nullable BigDecimal longitude, boolean ativo,
		int nivel, long filhasAtivas, long acessos) {

	public boolean raiz() {
		return unidadePaiId == null;
	}

}
