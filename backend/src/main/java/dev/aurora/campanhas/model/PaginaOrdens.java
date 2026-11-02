package dev.aurora.campanhas.model;

import java.util.List;

/** Página da lista de OS (ordem: prioridade, prazo, número). */
public record PaginaOrdens(List<OrdemServico> itens, long total, int pagina, int tamanho) {
}
