package dev.aurora.servicos.model;

import java.util.List;

/** Catálogo completo do cliente para a tela de cadastro. */
public record Catalogo(List<Grupo> grupos, List<Servico> servicos, List<Motivo> motivos, List<TipoVeiculo> veiculos,
		List<Habilidade> habilidades) {
}
