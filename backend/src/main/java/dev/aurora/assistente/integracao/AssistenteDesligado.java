package dev.aurora.assistente.integracao;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import dev.aurora.assistente.model.DefinicaoFerramenta;
import dev.aurora.assistente.model.RespostaDoModelo;
import dev.aurora.assistente.model.SaidaFerramenta;
import dev.aurora.assistente.model.TurnoAnterior;
import dev.aurora.assistente.service.AssistenteIndisponivelException;
import dev.aurora.assistente.service.ModeloDoAssistente;

/** Sem chave da API: a tela avisa e o histórico continua consultável. */
class AssistenteDesligado implements ModeloDoAssistente {

	@Override
	public boolean disponivel() {
		return false;
	}

	@Override
	public RespostaDoModelo responder(List<TurnoAnterior> historico, String pergunta,
			List<DefinicaoFerramenta> ferramentas, BiFunction<String, Map<String, Object>, SaidaFerramenta> executor) {
		throw new AssistenteIndisponivelException("O assistente está desligado neste ambiente.");
	}

}
