package dev.aurora.campanhas;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API das OS para a roteirização publicar a rota de uma equipe (ADR 0046). */
public interface ProgramacaoDeRotas {

	/**
	 * Programa as OS para a equipe na data, na ordem dada (sequência 1, 2, …).
	 * Planejadas, programadas e devolvidas viram programadas para a equipe; as que ela já
	 * tem em execução ou paralisadas só ganham a sequência. As demais OS da equipe no dia
	 * perdem a sequência. Ignora as que mudaram de situação ou deixaram de ser
	 * compatíveis (RN-23).
	 * @return OS programadas ou ressequenciadas
	 */
	int programarEmSequencia(UUID equipe, LocalDate data, List<UUID> ordens, UUID usuario);

}
