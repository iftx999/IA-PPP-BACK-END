package dev.aurora.campanhas.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Cadastro ou alteração de equipe. */
public record DadosEquipe(UUID unidadeId, String nome, TipoEquipe tipo, @Nullable String empresa, boolean ativa,
		List<UUID> membros) {
}
