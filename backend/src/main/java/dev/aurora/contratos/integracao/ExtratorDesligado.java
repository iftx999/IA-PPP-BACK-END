package dev.aurora.contratos.integracao;

import java.util.List;

import dev.aurora.contratos.model.ResultadoExtracao;
import dev.aurora.contratos.model.Trecho;
import dev.aurora.contratos.service.ExtratorContrato;

/** Sem chave de IA no ambiente: o texto é lido e os itens são cadastrados à mão. */
class ExtratorDesligado implements ExtratorContrato {

	@Override
	public boolean disponivel() {
		return false;
	}

	@Override
	public ResultadoExtracao extrair(List<Trecho> trechos, List<String> unidades) throws ExtracaoFalhouException {
		throw new ExtracaoFalhouException("Extração por IA não configurada.");
	}

}
