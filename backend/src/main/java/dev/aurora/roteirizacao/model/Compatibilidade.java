package dev.aurora.roteirizacao.model;

import java.util.UUID;

/** RN-23 para um par equipe × OS. */
public record Compatibilidade(UUID equipeId, UUID ordemId, boolean area, boolean habilidade, boolean veiculo) {

	public boolean atende() {
		return area && habilidade && veiculo;
	}

}
