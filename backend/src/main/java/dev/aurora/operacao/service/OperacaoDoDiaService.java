package dev.aurora.operacao.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.operacao.model.EquipeNoDia;
import dev.aurora.operacao.model.MapaDoDia;
import dev.aurora.operacao.model.RotaPublicada;
import dev.aurora.operacao.repository.OperacaoRepository;
import dev.aurora.operacao.repository.SugestaoRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapa do dia (J12): onde estão as equipes, a situação das OS e o atraso estimado de cada
 * equipe em jornada em relação à rota publicada.
 */
@Service
public class OperacaoDoDiaService {

	static final int DIAS_PARA_TRAS = 92;

	private final OperacaoRepository repositorio;

	private final SugestaoRepository sugestoes;

	private final Unidades unidades;

	private final Clock relogio;

	OperacaoDoDiaService(OperacaoRepository repositorio, SugestaoRepository sugestoes, Unidades unidades,
			Clock relogio) {
		this.repositorio = repositorio;
		this.sugestoes = sugestoes;
		this.unidades = unidades;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public MapaDoDia mapa(@Nullable UUID unidade, @Nullable LocalDate data) {
		var area = unidadeDaConsulta(unidades, unidade);
		var hoje = LocalDate.now(relogio);
		var dia = data != null ? data : hoje;
		if (dia.isAfter(hoje.plusDays(30)) || dia.isBefore(hoje.minusDays(DIAS_PARA_TRAS))) {
			throw new RegraVioladaException(
					"Escolha um dia entre " + DIAS_PARA_TRAS + " dias atrás e daqui a 30 dias.");
		}
		var agora = relogio.instant();
		var zona = relogio.getZone();
		var ordens = repositorio.ordensDoDia(area, dia, dia.equals(hoje), agora);
		var equipes = repositorio.equipesDoDia(area, dia, dia.atStartOfDay(zona).toInstant(),
				dia.plusDays(1).atStartOfDay(zona).toInstant());
		if (dia.equals(hoje)) {
			var emJornada = equipes.stream().filter(EquipeNoDia::emJornada).map(EquipeNoDia::id).toList();
			var rotas = new HashMap<UUID, RotaPublicada>();
			repositorio.rotasPublicadas(emJornada, dia).forEach(r -> rotas.put(r.equipeId(), r));
			equipes = equipes.stream().map(e -> {
				var rota = rotas.get(e.id());
				return rota == null || !e.emJornada() ? e
						: e.comRota(EstimativaDeAtraso.minutos(rota.paradas(), e.jornadaInicio(), agora),
								rota.fimPrevisto(), rota.aguardandoConfirmacao(e.jornadaInicio()), rota.confirmadaEm());
			}).toList();
		}
		return new MapaDoDia(dia, agora, equipes, ordens, sugestoes.abertasDoDia(area, dia));
	}

	/** A unidade pedida, se está no escopo; sem pedido, a mais alta do escopo. */
	static UUID unidadeDaConsulta(Unidades unidades, @Nullable UUID unidade) {
		if (unidade == null) {
			return unidades.principalDoEscopo()
				.orElseThrow(() -> new RegraVioladaException("Você não tem unidades no escopo."))
				.id();
		}
		return unidades.buscarNoEscopo(unidade)
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade da sua área."))
			.id();
	}

}
