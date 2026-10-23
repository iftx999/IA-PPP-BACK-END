package dev.aurora.assistente.model;

import java.util.Map;

/**
 * Linha de dados expansível ligada a uma fonte: os valores exatamente como a ferramenta
 * os leu do banco, com rótulos em português (meta, adesão, ligados, com rede...).
 */
public record LinhaDeDados(int fonte, String titulo, Map<String, String> campos) {
}
