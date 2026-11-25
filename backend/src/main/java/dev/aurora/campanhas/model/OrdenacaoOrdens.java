package dev.aurora.campanhas.model;

/**
 * Ordem da lista geral de OS. Sem escolha vale a ordem de trabalho: prioridade, prazo e
 * número.
 *
 * @param decrescente sem efeito na ordem de trabalho
 */
public record OrdenacaoOrdens(Campo campo, boolean decrescente) {

	public static final OrdenacaoOrdens TRABALHO = new OrdenacaoOrdens(Campo.PRIORIDADE, false);

	public enum Campo {

		PRIORIDADE, NUMERO, SERVICO, UNIDADE, PRAZO, EQUIPE, SITUACAO

	}

}
