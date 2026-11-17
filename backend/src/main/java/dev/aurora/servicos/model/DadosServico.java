package dev.aurora.servicos.model;

import java.util.UUID;

/**
 * Cadastro ou alteração de serviço. O código só é usado no cadastro: depois não muda
 * (integrações e OS antigas dependem dele).
 */
public record DadosServico(UUID grupoId, String codigo, boolean ativo, DefinicaoServico definicao) {
}
