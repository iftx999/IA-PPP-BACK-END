package dev.aurora.roteirizacao.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.ProgramacaoDeRotas;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.roteirizacao.model.OrdemParaRota;
import dev.aurora.roteirizacao.model.OrigemRoteirizacao;
import dev.aurora.roteirizacao.model.PedidoRoteirizacao;
import dev.aurora.roteirizacao.model.Roteirizacao;
import dev.aurora.roteirizacao.model.SituacaoRoteirizacao;
import dev.aurora.roteirizacao.repository.RoteirizacaoRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Roteirização (J6, J7; ADR 0046): o programador pede o cálculo das rotas de um dia para
 * as equipes e OS escolhidas; o cálculo roda em segundo plano e o resultado (estimativa,
 * RN-25) só mexe nas OS quando é publicado.
 */
@Service
public class RoteirizacaoService {

	static final int MAXIMO_DE_EQUIPES = 50;

	/** Escala de referência do doc 10 (10.8): 50 equipes × 1.500 OS. */
	static final int MAXIMO_DE_ORDENS = 1_500;

	static final int DIAS_A_FRENTE = 30;

	private static final java.util.Set<String> ROTEIRIZAVEIS = java.util.Set.of("PLANEJADA", "PROGRAMADA", "DEVOLVIDA");

	private final RoteirizacaoRepository repositorio;

	private final CalculoDaRoteirizacao calculo;

	private final ProgramacaoDeRotas programacao;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	RoteirizacaoService(RoteirizacaoRepository repositorio, CalculoDaRoteirizacao calculo,
			ProgramacaoDeRotas programacao, Unidades unidades, Auditoria auditoria, TransactionTemplate transacao,
			Clock relogio) {
		this.repositorio = repositorio;
		this.calculo = calculo;
		this.programacao = programacao;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * J6: valida o pedido, registra a roteirização (situação CALCULANDO) e dispara o
	 * cálculo em segundo plano.
	 */
	public Roteirizacao calcular(PedidoRoteirizacao pedido) {
		var contexto = ContextoAcesso.exigir();
		var id = UUID.randomUUID();
		var criada = transacao.execute(s -> {
			validar(pedido);
			try {
				repositorio.criar(id, pedido.unidadeId(), pedido.data(), pedido.origem(), pedido.anteriorId(),
						contexto.usuarioId());
			}
			catch (DuplicateKeyException e) {
				throw new ConflitoException(
						"Já há um cálculo em andamento para este dia e unidade. Aguarde ele terminar.");
			}
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("data", pedido.data().toString());
			detalhes.put("origem", pedido.origem().name());
			detalhes.put("equipes", pedido.equipes().size());
			detalhes.put("ordens", pedido.ordens().size());
			detalhes.put("anterior", pedido.anteriorId() == null ? null : pedido.anteriorId().toString());
			auditoria.registrar(TipoEvento.ROTEIRIZACAO_PEDIDA, "roteirizacao/" + id, detalhes);
			return repositorio.buscar(id).orElseThrow();
		});
		calculo.iniciar(contexto, id, pedido);
		return criada;
	}

	/**
	 * J6: recalcula a partir de uma roteirização, mantendo no começo de cada rota as OS
	 * fixadas (ou arrastadas) pelo programador.
	 */
	public Roteirizacao recalcular(UUID id, Map<UUID, List<UUID>> fixadas) {
		var anterior = buscar(id);
		if (anterior.situacao() == SituacaoRoteirizacao.CALCULANDO) {
			throw new ConflitoException("Aguarde o cálculo terminar.");
		}
		var equipes = anterior.rotas().stream().map(Roteirizacao.RotaCalculada::equipeId).toList();
		var ordens = new LinkedHashSet<UUID>();
		anterior.rotas().forEach(r -> r.paradas().forEach(p -> ordens.add(p.ordemServicoId())));
		anterior.pendentes().forEach(p -> ordens.add(p.ordemServicoId()));
		return calcular(new PedidoRoteirizacao(anterior.unidadeId(), anterior.data(), anterior.origem(), equipes,
				List.copyOf(ordens), fixadas, id));
	}

	/**
	 * J7: recalcula as rotas do dia das equipes a partir da última posição; OS em
	 * execução ficam fixas no começo.
	 */
	public Roteirizacao reotimizar(UUID unidade, List<UUID> equipes) {
		var hoje = LocalDate.now(relogio);
		var ordens = new LinkedHashSet<UUID>();
		var fixadas = new LinkedHashMap<UUID, List<UUID>>();
		transacao.executeWithoutResult(s -> {
			for (var equipe : new LinkedHashSet<>(equipes)) {
				var doDia = repositorio.ordensDoDia(equipe, hoje);
				ordens.addAll(doDia);
				var emExecucao = repositorio.ordens(doDia)
					.stream()
					.filter(o -> "EM_EXECUCAO".equals(o.status()))
					.map(OrdemParaRota::id)
					.toList();
				fixadas.put(equipe, emExecucao);
			}
		});
		if (ordens.isEmpty()) {
			throw new RegraVioladaException("As equipes escolhidas não têm OS abertas hoje.");
		}
		return calcular(new PedidoRoteirizacao(unidade, hoje, OrigemRoteirizacao.REOTIMIZACAO, equipes,
				List.copyOf(ordens), fixadas, null));
	}

	@Transactional(readOnly = true)
	public Roteirizacao buscar(UUID id) {
		return repositorio.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
	}

	@Transactional(readOnly = true)
	public List<Roteirizacao> listar(LocalDate data) {
		return repositorio.listar(data);
	}

	/**
	 * Publica: cada rota vira a programação da equipe no dia, na ordem calculada. A
	 * publicação anterior do mesmo dia e unidade deixa de valer.
	 */
	@Transactional
	public Roteirizacao publicar(UUID id) {
		var situacao = repositorio.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (situacao != SituacaoRoteirizacao.PRONTA) {
			throw new ConflitoException(switch (situacao) {
				case CALCULANDO -> "Aguarde o cálculo terminar.";
				case PUBLICADA -> "Esta roteirização já foi publicada.";
				default -> "Só uma roteirização pronta pode ser publicada.";
			});
		}
		var roteirizacao = buscar(id);
		if (roteirizacao.data().isBefore(LocalDate.now(relogio))) {
			throw new RegraVioladaException("Não dá para publicar a programação de um dia que já passou.");
		}
		var usuario = usuario();
		int programadas = 0;
		for (var rota : roteirizacao.rotas()) {
			var lista = rota.paradas().stream().map(Roteirizacao.ParadaCalculada::ordemServicoId).toList();
			programadas += programacao.programarEmSequencia(rota.equipeId(), roteirizacao.data(), lista, usuario);
		}
		repositorio.substituirPublicadas(roteirizacao.unidadeId(), roteirizacao.data(), id);
		repositorio.publicar(id, usuario);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("data", roteirizacao.data().toString());
		detalhes.put("rotas", roteirizacao.rotas().size());
		detalhes.put("programadas", programadas);
		detalhes.put("pendentes", roteirizacao.pendentes().size());
		auditoria.registrar(TipoEvento.ROTEIRIZACAO_PUBLICADA, "roteirizacao/" + id, detalhes);
		return buscar(id);
	}

	@Transactional
	public Roteirizacao descartar(UUID id) {
		var situacao = repositorio.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (situacao == SituacaoRoteirizacao.CALCULANDO || situacao == SituacaoRoteirizacao.PUBLICADA) {
			throw new ConflitoException(situacao == SituacaoRoteirizacao.CALCULANDO ? "Aguarde o cálculo terminar."
					: "A roteirização publicada só é substituída por outra publicação.");
		}
		repositorio.descartar(id);
		auditoria.registrar(TipoEvento.ROTEIRIZACAO_DESCARTADA, "roteirizacao/" + id, Map.of());
		return buscar(id);
	}

	private void validar(PedidoRoteirizacao pedido) {
		unidades.buscarNoEscopo(pedido.unidadeId())
			.orElseThrow(() -> new RegraVioladaException("Escolha uma unidade onde você programa OS."));
		var hoje = LocalDate.now(relogio);
		if (pedido.data().isBefore(hoje) || pedido.data().isAfter(hoje.plusDays(DIAS_A_FRENTE))) {
			throw new RegraVioladaException("A data deve ficar entre hoje e daqui a " + DIAS_A_FRENTE + " dias.");
		}
		var equipes = new LinkedHashSet<>(pedido.equipes());
		if (equipes.isEmpty() || equipes.size() > MAXIMO_DE_EQUIPES) {
			throw new RegraVioladaException("Escolha de 1 a " + MAXIMO_DE_EQUIPES + " equipes.");
		}
		var encontradas = repositorio.equipes(equipes);
		if (encontradas.size() != equipes.size()) {
			throw new RegraVioladaException("Escolha equipes da sua área.");
		}
		var desativada = encontradas.stream().filter(e -> !e.ativa()).findFirst();
		if (desativada.isPresent()) {
			throw new RegraVioladaException("A equipe " + desativada.get().nome() + " está desativada.");
		}
		var semPartida = encontradas.stream()
			.filter(e -> e.base() == null
					&& (pedido.origem() == OrigemRoteirizacao.PREVIA || e.ultimaPosicao() == null))
			.findFirst();
		if (semPartida.isPresent()) {
			throw new RegraVioladaException("A equipe " + semPartida.get().nome()
					+ " não tem base cadastrada. Marque a base no cadastro da equipe.");
		}
		var ordens = new LinkedHashSet<>(pedido.ordens());
		if (ordens.isEmpty() || ordens.size() > MAXIMO_DE_ORDENS) {
			throw new RegraVioladaException("Escolha de 1 a " + MAXIMO_DE_ORDENS + " OS.");
		}
		var lidas = repositorio.ordens(ordens);
		if (lidas.size() != ordens.size()) {
			throw new RegraVioladaException("Há OS fora da sua área ou inexistentes no pedido.");
		}
		var fora = new ArrayList<Long>();
		for (var ordem : lidas) {
			boolean daEquipeNoDia = ordem.emCampo() && ordem.equipeId() != null && equipes.contains(ordem.equipeId())
					&& pedido.data().equals(ordem.dataProgramada());
			if (!ROTEIRIZAVEIS.contains(ordem.status()) && !daEquipeNoDia) {
				fora.add(ordem.numero());
			}
		}
		if (!fora.isEmpty()) {
			throw new RegraVioladaException("Estas OS já estão com outra equipe em campo, encerradas ou canceladas: "
					+ fora.stream().limit(10).map(String::valueOf).toList() + (fora.size() > 10 ? "…" : "") + ".");
		}
		var fixadas = new HashSet<UUID>();
		pedido.fixadas().forEach((equipe, lista) -> {
			if (!equipes.contains(equipe)) {
				throw new RegraVioladaException("Só é possível fixar OS em equipes do cálculo.");
			}
			for (var os : lista) {
				if (!ordens.contains(os) || !fixadas.add(os)) {
					throw new RegraVioladaException("Cada OS fixada deve estar no cálculo e em uma só equipe.");
				}
			}
		});
	}

	private static UUID usuario() {
		var id = ContextoAcesso.exigir().usuarioId();
		if (id == null) {
			throw new IllegalStateException("Operação de usuário fora de uma requisição");
		}
		return id;
	}

}
