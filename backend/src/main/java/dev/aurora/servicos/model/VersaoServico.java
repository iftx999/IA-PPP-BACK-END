package dev.aurora.servicos.model;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Versão do serviço (RN-20).
 *
 * @param criadaPor nome de quem criou; nulo no catálogo inicial
 */
public record VersaoServico(int versao, Instant criadaEm, @Nullable String criadaPor, DefinicaoServico definicao) {
}
