package dev.aurora.campanhas;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** API da jornada do app de campo para os demais módulos (roteirização). */
public interface JornadasDoCampo {

	/**
	 * Equipe da jornada aberta da pessoa, conferindo o celular (cadastrado e não
	 * revogado, ADR 0045).
	 * @throws dev.aurora.comum.ConflitoException sem jornada aberta
	 */
	UUID equipeEmJornada(@Nullable UUID dispositivoId);

	/**
	 * A jornada aberta da pessoa (equipe e início), conferindo o celular.
	 * @throws dev.aurora.comum.ConflitoException sem jornada aberta
	 */
	JornadaAberta jornadaAberta(@Nullable UUID dispositivoId);

}
