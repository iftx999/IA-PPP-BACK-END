package dev.aurora.roteirizacao.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Resultado de um cálculo (sempre estimativa, RN-25).
 *
 * @param calculo como os deslocamentos foram calculados; nulo enquanto calcula
 */
public record Roteirizacao(UUID id, UUID unidadeId, String unidadeNome, LocalDate data, OrigemRoteirizacao origem,
		SituacaoRoteirizacao situacao, @Nullable Calculo calculo, @Nullable String erro, @Nullable UUID anteriorId,
		Instant criadaEm, @Nullable String criadaPor, @Nullable Instant concluidaEm, @Nullable Instant publicadaEm,
		List<RotaCalculada> rotas, List<Pendente> pendentes) {

	public Roteirizacao com(List<RotaCalculada> listaRotas, List<Pendente> listaPendentes) {
		return new Roteirizacao(id, unidadeId, unidadeNome, data, origem, situacao, calculo, erro, anteriorId, criadaEm,
				criadaPor, concluidaEm, publicadaEm, listaRotas, listaPendentes);
	}

	/**
	 * @param tracado linhas do traçado pela malha (vazio sem Valhalla)
	 */
	public record RotaCalculada(UUID equipeId, String equipeNome, @Nullable Coordenada partida, Instant inicio,
			Instant fimPrevisto, double km, int minutosDeslocamento, int minutosServico, List<List<Coordenada>> tracado,
			List<ParadaCalculada> paradas) {
	}

	public record ParadaCalculada(UUID ordemServicoId, long numero, String servicoNome, String prioridade, int ordem,
			Instant chegadaPrevista, Instant saidaPrevista, double kmAte, int minutosAte, boolean fixada,
			boolean atrasa, @Nullable Coordenada local) {
	}

	public record Pendente(UUID ordemServicoId, long numero, String servicoNome, String prioridade,
			MotivoPendente motivo) {
	}

}
