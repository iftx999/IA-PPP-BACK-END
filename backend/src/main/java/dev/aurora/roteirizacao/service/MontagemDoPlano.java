package dev.aurora.roteirizacao.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import dev.aurora.comum.RegraVioladaException;
import dev.aurora.roteirizacao.model.Compatibilidade;
import dev.aurora.roteirizacao.model.Coordenada;
import dev.aurora.roteirizacao.model.EquipeParaRota;
import dev.aurora.roteirizacao.model.Matriz;
import dev.aurora.roteirizacao.model.MotivoPendente;
import dev.aurora.roteirizacao.model.OrdemParaRota;
import dev.aurora.roteirizacao.model.OrigemRoteirizacao;
import dev.aurora.roteirizacao.model.PerfilRota;
import dev.aurora.roteirizacao.model.Roteirizacao.ParadaCalculada;
import dev.aurora.roteirizacao.model.Roteirizacao.Pendente;
import dev.aurora.roteirizacao.model.Roteirizacao.RotaCalculada;
import dev.aurora.roteirizacao.otimizacao.LinhaDoTempo;
import dev.aurora.roteirizacao.otimizacao.Parada;
import dev.aurora.roteirizacao.otimizacao.PlanoDoDia;
import dev.aurora.roteirizacao.otimizacao.PrioridadeDaParada;
import dev.aurora.roteirizacao.otimizacao.RotaDaEquipe;
import org.jspecify.annotations.Nullable;

/**
 * Do pedido ao problema do Timefold e de volta (RN-25): pontos da matriz (partidas,
 * chegadas e saídas), paradas com as equipes compatíveis (RN-23), rotas com a jornada e
 * as fixadas, e a conversão da solução em horários. Os tempos do otimizador são segundos
 * desde a meia-noite do dia, no fuso do cliente.
 */
final class MontagemDoPlano {

	private final LocalDate data;

	private final ZoneId fuso;

	private final OrigemRoteirizacao origem;

	private final Instant agora;

	/** Pontos da matriz, na ordem dos índices. */
	private final Map<Coordenada, Integer> pontos = new LinkedHashMap<>();

	private final Map<UUID, OrdemParaRota> ordens = new LinkedHashMap<>();

	private final Map<UUID, Parada> paradas = new LinkedHashMap<>();

	private final Map<UUID, EquipeParaRota> equipes = new LinkedHashMap<>();

	private final Map<UUID, Coordenada> partidas = new HashMap<>();

	private final List<Pendente> pendentes = new ArrayList<>();

	MontagemDoPlano(LocalDate data, ZoneId fuso, OrigemRoteirizacao origem, Instant agora) {
		this.data = data;
		this.fuso = fuso;
		this.origem = origem;
		this.agora = agora;
	}

	/**
	 * Separa as OS sem local e as que nenhuma equipe pode atender (pendentes antes do
	 * cálculo) e registra os pontos das demais e das partidas.
	 */
	MontagemDoPlano preparar(List<EquipeParaRota> listaEquipes, List<OrdemParaRota> listaOrdens,
			List<Compatibilidade> compatibilidades) {
		for (var equipe : listaEquipes) {
			var partida = partida(equipe);
			if (partida == null) {
				throw new RegraVioladaException("A equipe " + equipe.nome()
						+ " não tem base cadastrada nem posição do dia. Cadastre a base em Equipes.");
			}
			equipes.put(equipe.id(), equipe);
			partidas.put(equipe.id(), partida);
			indice(partida);
		}
		var porOrdem = new HashMap<UUID, List<Compatibilidade>>();
		compatibilidades.forEach(c -> porOrdem.computeIfAbsent(c.ordemId(), k -> new ArrayList<>()).add(c));
		for (var ordem : listaOrdens) {
			ordens.put(ordem.id(), ordem);
			var entrada = ordem.entrada();
			var saida = ordem.saida();
			if (entrada == null || saida == null) {
				pendentes.add(pendente(ordem, MotivoPendente.SEM_LOCAL));
				continue;
			}
			var lista = porOrdem.getOrDefault(ordem.id(), List.of());
			var compativeis = new HashSet<UUID>();
			lista.stream().filter(Compatibilidade::atende).forEach(c -> compativeis.add(c.equipeId()));
			if (compativeis.isEmpty()) {
				pendentes.add(pendente(ordem, motivoDaIncompatibilidade(lista)));
				continue;
			}
			paradas.put(ordem.id(), new Parada(ordem.id(), prioridade(ordem.prioridade()), indice(entrada),
					indice(saida), Math.max(60, ordem.duracaoSeg()), prazoSeg(ordem.prazo()), compativeis));
		}
		return this;
	}

	List<Coordenada> pontos() {
		return List.copyOf(pontos.keySet());
	}

	Set<PerfilRota> perfis() {
		var perfis = new HashSet<PerfilRota>();
		equipes.values().forEach(e -> perfis.add(e.perfil()));
		return perfis;
	}

	/**
	 * O problema, com as matrizes por perfil já calculadas e as fixadas de cada equipe
	 * (na ordem dada; ignoradas as que a equipe não pode atender ou já são pendentes).
	 */
	PlanoDoDia plano(Map<PerfilRota, Matriz> matrizes, Map<UUID, List<UUID>> fixadas) {
		var rotas = new ArrayList<RotaDaEquipe>();
		var usadas = new HashSet<UUID>();
		for (var equipe : equipes.values()) {
			var inicio = inicioSeg(equipe);
			var fim = (long) equipe.jornadaFim().toSecondOfDay();
			var rota = new RotaDaEquipe(equipe.id(), matrizes.get(equipe.perfil()), indice(partidas.get(equipe.id())),
					inicio, Math.max(inicio, fim), equipe.pausaMin() * 60);
			var lista = new ArrayList<Parada>();
			for (var id : fixadas.getOrDefault(equipe.id(), List.of())) {
				var parada = paradas.get(id);
				if (parada != null && parada.atendidaPor(equipe.id()) && usadas.add(id)) {
					lista.add(parada);
				}
			}
			rota.fixar(lista);
			rotas.add(rota);
		}
		return new PlanoDoDia(rotas, List.copyOf(paradas.values()));
	}

	/** Rotas com horários, e as OS que ficaram de fora por não caber (JORNADA). */
	Resultado resultado(PlanoDoDia solucao, BiFunction<List<Coordenada>, PerfilRota, List<List<Coordenada>>> tracar) {
		var rotas = new ArrayList<RotaCalculada>();
		var alocadas = new HashSet<UUID>();
		var coordenadas = pontos();
		for (var rota : solucao.getRotas()) {
			var equipe = equipes.get(rota.getEquipeId());
			var linha = rota.linhaDoTempo();
			var itens = new ArrayList<ParadaCalculada>();
			var trajeto = new ArrayList<Coordenada>();
			trajeto.add(partidas.get(equipe.id()));
			long servico = 0;
			for (int i = 0; i < linha.itens().size(); i++) {
				LinhaDoTempo.Item item = linha.itens().get(i);
				var parada = item.parada();
				var ordem = ordens.get(parada.getOrdemServicoId());
				alocadas.add(ordem.id());
				servico += parada.getDuracaoSeg();
				var local = coordenadas.get(parada.getEntrada());
				trajeto.add(local);
				if (parada.getSaida() != parada.getEntrada()) {
					trajeto.add(coordenadas.get(parada.getSaida()));
				}
				itens.add(new ParadaCalculada(ordem.id(), ordem.numero(), ordem.servicoNome(), ordem.prioridade(),
						i + 1, instante(item.chegadaSeg()), instante(item.saidaSeg()), item.metros() / 1000.0,
						(int) Math.round(item.viagemSeg() / 60.0), i < rota.getFixadas(),
						item.chegadaSeg() > parada.getPrazoSeg(), local));
			}
			var tracado = itens.isEmpty() ? List.<List<Coordenada>>of() : tracar.apply(trajeto, equipe.perfil());
			rotas.add(new RotaCalculada(equipe.id(), equipe.nome(), partidas.get(equipe.id()),
					instante(linha.inicioSeg()), instante(linha.fimSeg()), linha.metros() / 1000.0,
					(int) Math.round(linha.deslocamentoSeg() / 60.0), (int) Math.round(servico / 60.0), tracado,
					itens));
		}
		var todas = new ArrayList<>(pendentes);
		paradas.keySet()
			.stream()
			.filter(id -> !alocadas.contains(id))
			.forEach(id -> todas.add(pendente(ordens.get(id), MotivoPendente.JORNADA)));
		return new Resultado(rotas, todas);
	}

	record Resultado(List<RotaCalculada> rotas, List<Pendente> pendentes) {
	}

	/**
	 * Prévia parte da base; jornada e reotimização, da última posição do agente (ou da
	 * base, se ainda não há posição).
	 */
	private @Nullable Coordenada partida(EquipeParaRota equipe) {
		return origem == OrigemRoteirizacao.PREVIA ? (equipe.base() != null ? equipe.base() : equipe.ultimaPosicao())
				: (equipe.ultimaPosicao() != null ? equipe.ultimaPosicao() : equipe.base());
	}

	/** Início da jornada, ou agora quando a jornada do dia já começou (J7, J8). */
	private long inicioSeg(EquipeParaRota equipe) {
		long inicio = equipe.jornadaInicio().toSecondOfDay();
		if (origem == OrigemRoteirizacao.PREVIA) {
			return inicio;
		}
		long agoraSeg = Duration.between(meiaNoite(), agora).toSeconds();
		return Math.max(inicio, agoraSeg);
	}

	private long prazoSeg(@Nullable Instant prazo) {
		return prazo == null ? Parada.SEM_PRAZO : Duration.between(meiaNoite(), prazo).toSeconds();
	}

	private Instant instante(long segundos) {
		return meiaNoite().plusSeconds(segundos);
	}

	private Instant meiaNoite() {
		return data.atStartOfDay(fuso).toInstant();
	}

	private int indice(Coordenada ponto) {
		return pontos.computeIfAbsent(ponto, p -> pontos.size());
	}

	private static Pendente pendente(OrdemParaRota ordem, MotivoPendente motivo) {
		return new Pendente(ordem.id(), ordem.numero(), ordem.servicoNome(), ordem.prioridade(), motivo);
	}

	/** O primeiro requisito da RN-23 que nenhuma equipe escolhida cumpre. */
	static MotivoPendente motivoDaIncompatibilidade(List<Compatibilidade> lista) {
		var falhas = new EnumMap<MotivoPendente, Boolean>(MotivoPendente.class);
		falhas.put(MotivoPendente.AREA, lista.stream().noneMatch(Compatibilidade::area));
		falhas.put(MotivoPendente.HABILIDADE, lista.stream().noneMatch(c -> c.area() && c.habilidade()));
		return falhas.get(MotivoPendente.AREA) ? MotivoPendente.AREA
				: falhas.get(MotivoPendente.HABILIDADE) ? MotivoPendente.HABILIDADE : MotivoPendente.VEICULO;
	}

	static PrioridadeDaParada prioridade(String valor) {
		return PrioridadeDaParada.valueOf(valor);
	}

}
