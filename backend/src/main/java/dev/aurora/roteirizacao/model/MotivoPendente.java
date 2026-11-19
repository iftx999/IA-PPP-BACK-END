package dev.aurora.roteirizacao.model;

/** Por que a OS não entrou em nenhuma rota (J6). */
public enum MotivoPendente {

	/** Não coube na jornada de nenhuma equipe compatível. */
	JORNADA,
	/** Nenhuma equipe escolhida tem a habilidade exigida (RN-23). */
	HABILIDADE,
	/** Nenhuma equipe escolhida tem veículo que atende o serviço (RN-23). */
	VEICULO,
	/** Nenhuma equipe escolhida atua na unidade da OS (RN-23). */
	AREA,
	/** Imóvel sem localização ou trecho sem traçado. */
	SEM_LOCAL

}
