package dev.aurora.unidades;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo de unidades para os demais módulos (sempre dentro do escopo do
 * usuário).
 */
public interface Unidades {

	/**
	 * Unidade ativa do escopo; vazio se não existe, é de outro tenant ou está fora do
	 * escopo.
	 */
	Optional<Unidade> buscarNoEscopo(UUID id);

	/** Unidade mais alta do escopo, preferindo a concessão ao grupo econômico. */
	Optional<Unidade> principalDoEscopo();

}
