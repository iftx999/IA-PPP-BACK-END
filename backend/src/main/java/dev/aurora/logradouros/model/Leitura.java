package dev.aurora.logradouros.model;

import java.util.List;

/** Itens válidos e recusados de um arquivo lido. */
public record Leitura<T>(int lidos, List<T> itens, List<Rejeicao> rejeicoes) {
}
