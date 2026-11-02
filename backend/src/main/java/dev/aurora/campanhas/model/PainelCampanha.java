package dev.aurora.campanhas.model;

import java.util.List;

/**
 * Painel da campanha (I4): funil, produtividade por equipe, motivos e o efeito real na
 * adesão (agora × na criação).
 */
public record PainelCampanha(Campanha campanha, List<Produtividade> equipes, List<ContagemMotivo> motivos,
		ImpactoNaMeta agora) {
}
