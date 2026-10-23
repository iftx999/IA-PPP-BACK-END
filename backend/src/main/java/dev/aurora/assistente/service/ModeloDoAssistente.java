package dev.aurora.assistente.service;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.RespostaDoModelo;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.model.TurnoAnterior;

/**
 * Porta para o modelo de linguagem: conduz o laço de ferramentas até a resposta final. O
 * modelo só vê os dados pelas ferramentas; nunca recebe SQL nem escreve consultas.
 */
public interface ModeloDoAssistente {

	boolean disponivel();

	/**
	 * @param executor executa a ferramenta pedida (nome, entrada já decodificada)
	 */
	RespostaDoModelo responder(List<TurnoAnterior> historico, String pergunta, List<DefinicaoFerramenta> ferramentas,
			BiFunction<String, Map<String, Object>, SaidaFerramenta> executor);

}
