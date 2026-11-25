package dev.aurora.campanhas.model;

import java.util.List;

import dev.aurora.servicos.ParametroDaOrdem;

/**
 * O que o campo registrou numa OS: eventos, fotos, conflitos e os parâmetros da versão do
 * serviço (para mostrar os valores com os rótulos).
 */
public record ExecucaoDaOrdem(List<EventoDaOrdem> eventos, List<Anexo> anexos, List<Conflito> conflitos,
		List<ParametroDaOrdem> parametros) {

	public ExecucaoDaOrdem {
		eventos = List.copyOf(eventos);
		anexos = List.copyOf(anexos);
		conflitos = List.copyOf(conflitos);
		parametros = List.copyOf(parametros);
	}

}
