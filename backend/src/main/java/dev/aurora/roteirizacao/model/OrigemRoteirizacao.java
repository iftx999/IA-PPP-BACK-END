package dev.aurora.roteirizacao.model;

/** Quem pediu o cálculo (rota.origem, doc 10, 10.10). */
public enum OrigemRoteirizacao {

	/** Programador, antes do dia: parte da base das equipes. */
	PREVIA,
	/** "Iniciar jornada" no app: parte da posição do agente (J8). */
	JORNADA,
	/** Durante o dia (J7): parte da última posição; OS em execução ficam fixas. */
	REOTIMIZACAO

}
