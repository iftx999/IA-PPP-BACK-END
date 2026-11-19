package dev.aurora.roteirizacao.model;

/** Perfil de deslocamento do veículo da equipe (tipo_veiculo.perfil_rota). */
public enum PerfilRota {

	MOTO, CARRO, CAMINHAO;

	/** Equipe sem veículo cadastrado anda como carro (estimativa conservadora). */
	public static PerfilRota de(String valor) {
		return valor == null ? CARRO : valueOf(valor);
	}

}
