package dev.aurora.campanhas.model;

import java.util.List;

/** Caixa de conflitos de campo (J11). */
public record PaginaConflitos(List<Conflito> itens, long total, long abertos, int pagina, int tamanho) {

	public PaginaConflitos {
		itens = List.copyOf(itens);
	}

}
