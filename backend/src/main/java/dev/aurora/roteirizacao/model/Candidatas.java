package dev.aurora.roteirizacao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * O que dá para roteirizar numa unidade e dia: OS planejadas e devolvidas, as programadas
 * para o dia, e as equipes ativas que atuam na unidade.
 *
 * @param truncada havia mais OS que o limite de um cálculo
 */
public record Candidatas(List<OrdemCandidata> ordens, boolean truncada, List<EquipeCandidata> equipes) {

	/**
	 * @param temLocal imóvel com localização ou trecho com traçado
	 */
	public record OrdemCandidata(UUID id, long numero, String servicoNome, String prioridade, String status,
			@Nullable Instant prazoEm, boolean atrasada, @Nullable UUID equipeId, @Nullable String equipeNome,
			@Nullable LocalDate dataProgramada, boolean temLocal) {
	}

	public record EquipeCandidata(UUID id, String nome, boolean temBase, @Nullable String veiculoNome,
			LocalTime jornadaInicio, LocalTime jornadaFim, long habilidades) {
	}

}
