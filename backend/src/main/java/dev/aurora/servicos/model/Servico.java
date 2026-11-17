package dev.aurora.servicos.model;

import java.time.Instant;
import java.util.UUID;

/** Serviço do catálogo com a definição da versão atual. */
public record Servico(UUID id, UUID grupoId, String codigo, boolean ativo, int versao, Instant atualizadoEm,
		DefinicaoServico definicao) {
}
