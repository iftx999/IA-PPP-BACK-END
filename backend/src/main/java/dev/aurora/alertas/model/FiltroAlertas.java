package dev.aurora.alertas.model;

import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Filtro da lista de alertas. Tipos vazios valem todos; a unidade inclui as unidades
 * abaixo dela (o RLS continua limitando ao escopo de quem consulta).
 */
public record FiltroAlertas(EstadoAlerta estado, Set<TipoAlerta> tipos, @Nullable Severidade severidade,
		@Nullable UUID unidade) {

	public FiltroAlertas {
		tipos = Set.copyOf(tipos);
	}

}
