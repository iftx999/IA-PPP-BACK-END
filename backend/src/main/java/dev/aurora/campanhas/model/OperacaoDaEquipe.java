package dev.aurora.campanhas.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * O que a roteirização precisa da equipe (RN-23, RN-25; ADR 0046).
 *
 * @param base ponto de partida da prévia; no "Iniciar jornada" vale a posição do agente
 * @param pausaMin almoço e paradas, descontados da jornada
 * @param tipoVeiculoId veículo da equipe; sem ele, só serviços que não exigem veículo
 * @param habilidades habilidades do catálogo que a equipe tem
 */
public record OperacaoDaEquipe(@Nullable Posicao base, LocalTime jornadaInicio, LocalTime jornadaFim, int pausaMin,
		@Nullable UUID tipoVeiculoId, List<UUID> habilidades) {

	public static final LocalTime INICIO_PADRAO = LocalTime.of(7, 30);

	public static final LocalTime FIM_PADRAO = LocalTime.of(16, 30);

	public static final int PAUSA_PADRAO = 60;

	public OperacaoDaEquipe {
		habilidades = List.copyOf(habilidades);
	}

	/** Jornada padrão, sem base, veículo nem habilidades. */
	public static OperacaoDaEquipe padrao() {
		return new OperacaoDaEquipe(null, INICIO_PADRAO, FIM_PADRAO, PAUSA_PADRAO, null, List.of());
	}

	/** Tempo de trabalho do dia: jornada menos a pausa. */
	public Duration tempoUtil() {
		return Duration.between(jornadaInicio, jornadaFim).minusMinutes(pausaMin);
	}

	public OperacaoDaEquipe comHabilidades(List<UUID> lista) {
		return new OperacaoDaEquipe(base, jornadaInicio, jornadaFim, pausaMin, tipoVeiculoId, lista);
	}

}
