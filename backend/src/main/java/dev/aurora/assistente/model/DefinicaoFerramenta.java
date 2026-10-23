package dev.aurora.assistente.model;

import java.util.List;
import java.util.Map;

/**
 * Ferramenta oferecida ao modelo: nome, descrição e esquema JSON das propriedades (todas
 * obrigatórias; opcionais aceitam null).
 */
public record DefinicaoFerramenta(String nome, String descricao, Map<String, Object> propriedades,
		List<String> obrigatorias) {
}
