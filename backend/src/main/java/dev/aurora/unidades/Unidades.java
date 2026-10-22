package dev.aurora.unidades;

import java.util.List;
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

	/**
	 * Caminho ltree da unidade ativa do escopo, para conferir onde uma permissão vale
	 * ({@code ContextoAcesso.podeEm}).
	 */
	Optional<String> caminhoNoEscopo(UUID id);

	/** A unidade e as ativas abaixo dela, dentro do escopo (vazio se ela está fora). */
	List<Unidade> subarvore(UUID raiz);

}
