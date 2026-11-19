package dev.aurora.campanhas.model;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Equipe de campo (ADR 0036): atua na unidade informada e nas de baixo.
 *
 * @param empresa nome da empreiteira; só em equipe terceirizada
 * @param ordensEmCampo OS com a equipe esperando resultado
 * @param operacao base, jornada, veículo e habilidades (RN-23, RN-25)
 */
public record Equipe(UUID id, UUID unidadeId, String unidadeNome, String nome, TipoEquipe tipo,
		@Nullable String empresa, boolean ativa, List<Membro> membros, long ordensEmCampo, OperacaoDaEquipe operacao) {

	public Equipe comMembros(List<Membro> lista) {
		return new Equipe(id, unidadeId, unidadeNome, nome, tipo, empresa, ativa, lista, ordensEmCampo, operacao);
	}

	public Equipe comHabilidades(List<UUID> lista) {
		return new Equipe(id, unidadeId, unidadeNome, nome, tipo, empresa, ativa, membros, ordensEmCampo,
				operacao.comHabilidades(lista));
	}

}
