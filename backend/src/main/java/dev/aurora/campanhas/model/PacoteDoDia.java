package dev.aurora.campanhas.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.servicos.MotivoDaOrdem;
import dev.aurora.servicos.ParametroDaOrdem;
import org.jspecify.annotations.Nullable;

/**
 * Pacote do dia da equipe (10.9.2): OS programadas, versões dos serviços com os
 * parâmetros e os motivos. Com {@code diferenca}, {@code ordens} traz só as alteradas
 * desde a versão que o celular tinha; {@code ids} são sempre todas as OS do pacote (o
 * celular remove as que saíram).
 *
 * @param versao muda quando alguma OS do pacote muda
 * @param raioInicioM RN-22: distância do alvo acima da qual o início fica marcado
 */
public record PacoteDoDia(long versao, boolean diferenca, UUID equipeId, String equipeNome, @Nullable Jornada jornada,
		List<UUID> ids, List<OrdemDoPacote> ordens, Map<UUID, List<ParametroDaOrdem>> parametros,
		List<MotivoDaOrdem> motivos, int raioInicioM, Instant geradoEm) {

	public PacoteDoDia {
		ids = List.copyOf(ids);
		ordens = List.copyOf(ordens);
		parametros = Map.copyOf(parametros);
		motivos = List.copyOf(motivos);
	}

	/**
	 * OS com a versão do serviço (chave dos parâmetros).
	 *
	 * @param ultimaSequencia último evento do campo já processado nesta OS
	 */
	public record OrdemDoPacote(OrdemServico ordem, UUID servicoVersaoId, int ultimaSequencia) {
	}

}
