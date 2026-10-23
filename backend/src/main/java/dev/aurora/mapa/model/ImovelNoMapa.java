package dev.aurora.mapa.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Imóvel localizado: matrícula (mascarada sem {@code dados_pessoais:ler} na unidade),
 * ponto e situação; nunca nome nem endereço (RN-09).
 */
public record ImovelNoMapa(String matricula, UUID unidadeId, String unidadeNome, String caminhoUnidade,
		SituacaoNoMapa situacao, Precisao precisao, double longitude, double latitude, Instant localizadoEm) {

	public ImovelNoMapa comMatricula(String outra) {
		return new ImovelNoMapa(outra, unidadeId, unidadeNome, caminhoUnidade, situacao, precisao, longitude, latitude,
				localizadoEm);
	}

}
