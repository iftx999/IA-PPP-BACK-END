package dev.aurora.roteirizacao;

import java.util.List;
import java.util.UUID;

/**
 * API pública da roteirização para os demais módulos: pedir a reotimização do dia (J7)
 * quando um gatilho do acompanhamento é aceito pelo programador (ADR 0049).
 */
public interface Reotimizacoes {

	/**
	 * Recalcula hoje as rotas das equipes a partir da última posição de cada uma; OS em
	 * execução ficam fixas. O resultado é uma roteirização de origem REOTIMIZACAO que o
	 * programador confere e publica.
	 * @param extras OS ainda sem equipe no dia a incluir (ex.: emergencial nova)
	 * @return id da roteirização em cálculo
	 */
	UUID pedirReotimizacao(UUID unidade, List<UUID> equipes, List<UUID> extras);

}
