package dev.aurora.campanhas.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.aurora.campanhas.JornadaAberta;
import dev.aurora.campanhas.JornadaIniciada;
import dev.aurora.campanhas.JornadasDoCampo;
import dev.aurora.campanhas.model.AmostraDePosicao;
import dev.aurora.campanhas.model.EquipeDoDia;
import dev.aurora.campanhas.model.Jornada;
import dev.aurora.campanhas.model.PacoteDoDia;
import dev.aurora.campanhas.model.Posicao;
import dev.aurora.campanhas.repository.ContatoRepository;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.campanhas.repository.ExecucaoRepository;
import dev.aurora.campanhas.repository.JornadaRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.servicos.FaseComFoto;
import dev.aurora.servicos.Servicos;
import org.jspecify.annotations.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jornada da equipe no app (J8; ADR 0045): abrir com a posição, baixar o pacote do dia
 * (ou a diferença), enviar posições só com a jornada aberta (RN-27) e encerrar sem OS em
 * execução. Abrir a jornada pede a rota do dia a partir da posição do agente
 * ({@link JornadaIniciada}, ADR 0048).
 */
@Service
public class JornadaService implements JornadasDoCampo {

	static final int MAXIMO_DE_POSICOES = 500;

	/** Hora do celular adiantada além disso é trocada pela do servidor. */
	static final Duration ADIANTAMENTO_ACEITO = Duration.ofMinutes(5);

	private static final Set<String> MOTIVOS_DO_CAMPO = Set.of("PARALISACAO", "DEVOLUCAO", "NAO_EXECUCAO");

	private final JornadaRepository jornadas;

	private final EquipeRepository equipes;

	private final OrdemServicoRepository ordens;

	private final ExecucaoRepository execucao;

	private final DispositivoService dispositivos;

	private final DadosPessoaisDasOrdens dadosPessoais;

	private final Servicos servicos;

	private final Clock relogio;

	private final ApplicationEventPublisher eventos;

	private final ContatoRepository contatos;

	JornadaService(JornadaRepository jornadas, EquipeRepository equipes, OrdemServicoRepository ordens,
			ExecucaoRepository execucao, DispositivoService dispositivos, DadosPessoaisDasOrdens dadosPessoais,
			Servicos servicos, Clock relogio, ApplicationEventPublisher eventos, ContatoRepository contatos) {
		this.jornadas = jornadas;
		this.equipes = equipes;
		this.ordens = ordens;
		this.execucao = execucao;
		this.dispositivos = dispositivos;
		this.dadosPessoais = dadosPessoais;
		this.servicos = servicos;
		this.relogio = relogio;
		this.eventos = eventos;
		this.contatos = contatos;
	}

	/**
	 * Abre a jornada da equipe (idempotente pelo id gerado no celular). Se outro membro
	 * já abriu, o celular entra na mesma jornada.
	 * @param equipe obrigatória para quem está em mais de uma equipe
	 */
	@Transactional
	public PacoteDoDia iniciar(@Nullable UUID dispositivoId, UUID id, @Nullable UUID equipe, Instant ocorrido,
			Posicao posicao) {
		var dispositivo = dispositivos.exigir(dispositivoId, false);
		var minhas = equipes.equipesDe(OrdemServicoService.usuario());
		var existente = jornadas.buscar(id);
		if (existente.isPresent()) {
			return pacote(existente.get().equipeId(), null);
		}
		var escolhida = equipe != null
				? minhas.stream().filter(equipe::equals).findFirst().orElseThrow(RecursoNaoEncontradoException::new)
				: unica(minhas);
		var inicio = ocorrido.isAfter(agora().plus(ADIANTAMENTO_ACEITO)) ? agora() : ocorrido;
		// Falso quando outro membro já abriu a jornada da equipe: a rota já foi pedida
		// por ele.
		if (jornadas.abrir(id, escolhida, OrdemServicoService.usuario(), dispositivo.id(), inicio, posicao)) {
			eventos.publishEvent(new JornadaIniciada(ContextoAcesso.exigir().tenantId(), id, escolhida,
					OrdemServicoService.usuario(), LocalDate.ofInstant(agora(), relogio.getZone())));
		}
		return pacote(escolhida, null);
	}

	@Override
	@Transactional
	public UUID equipeEmJornada(@Nullable UUID dispositivoId) {
		dispositivos.exigir(dispositivoId, false);
		return jornadas.abertas(equipes.equipesDe(OrdemServicoService.usuario()))
			.stream()
			.map(Jornada::equipeId)
			.findFirst()
			.orElseThrow(() -> new ConflitoException("Não há jornada aberta."));
	}

	@Override
	@Transactional
	public JornadaAberta jornadaAberta(@Nullable UUID dispositivoId) {
		dispositivos.exigir(dispositivoId, false);
		return jornadas.abertas(equipes.equipesDe(OrdemServicoService.usuario()))
			.stream()
			.map(j -> new JornadaAberta(j.id(), j.equipeId(), j.inicio()))
			.findFirst()
			.orElseThrow(() -> new ConflitoException("Não há jornada aberta."));
	}

	/** J8: equipes da pessoa com o resumo de hoje, para escolher com qual sai. */
	@Transactional
	public List<EquipeDoDia> equipesDoDia(@Nullable UUID dispositivoId) {
		dispositivos.exigir(dispositivoId, false);
		return equipes.doDia(OrdemServicoService.usuario(), LocalDate.ofInstant(agora(), relogio.getZone()));
	}

	/**
	 * J8: encerra a jornada aberta das equipes da pessoa; nenhuma OS pode estar em
	 * execução.
	 */
	@Transactional
	public Jornada encerrar(@Nullable UUID dispositivoId, Instant ocorrido) {
		dispositivos.exigir(dispositivoId, false);
		var abertas = jornadas.abertas(equipes.equipesDe(OrdemServicoService.usuario()));
		if (abertas.isEmpty()) {
			throw new ConflitoException("Não há jornada aberta.");
		}
		for (var jornada : abertas) {
			if (jornadas.emExecucao(jornada.equipeId()) > 0) {
				throw new ConflitoException("Paralise ou encerre a OS em execução antes de encerrar a jornada.");
			}
		}
		var fim = ocorrido.isAfter(agora()) ? agora() : ocorrido;
		abertas.forEach(j -> jornadas.encerrar(j.id(), fim));
		return jornadas.buscar(abertas.getFirst().id()).orElseThrow();
	}

	/**
	 * Pacote do dia (10.9.2) da equipe com jornada aberta (ou da indicada, ou da única).
	 * @param desde versão que o celular já tem: só as OS alteradas desde então
	 */
	@Transactional
	public PacoteDoDia pacote(@Nullable UUID dispositivoId, @Nullable Long desde, @Nullable UUID equipe) {
		dispositivos.exigir(dispositivoId, false);
		var minhas = equipes.equipesDe(OrdemServicoService.usuario());
		UUID escolhida;
		if (equipe != null) {
			escolhida = minhas.stream()
				.filter(equipe::equals)
				.findFirst()
				.orElseThrow(RecursoNaoEncontradoException::new);
		}
		else {
			escolhida = jornadas.abertas(minhas)
				.stream()
				.map(Jornada::equipeId)
				.findFirst()
				.orElseGet(() -> unica(minhas));
		}
		return pacote(escolhida, desde);
	}

	private PacoteDoDia pacote(UUID equipe, @Nullable Long desde) {
		var agora = agora();
		var hoje = LocalDate.ofInstant(agora, relogio.getZone());
		var noPacote = ordens.doPacote(equipe, hoje);
		var versao = noPacote.stream().mapToLong(o -> o.atualizadaEm().toEpochMilli()).max().orElse(0);
		var alteradas = noPacote.stream()
			.filter(o -> desde == null || o.atualizadaEm().toEpochMilli() > desde)
			.toList();
		var versaoDaOrdem = new HashMap<UUID, UUID>();
		var sequencias = new HashMap<UUID, Integer>();
		alteradas.forEach(o -> {
			versaoDaOrdem.put(o.id(), o.servicoVersaoId());
			sequencias.put(o.id(), o.ultimaSequencia());
		});
		var detalhes = dadosPessoais.aplicar(ordens.buscarVarias(versaoDaOrdem.keySet(), agora),
				"Pacote do dia do app de campo");
		var parametros = servicos.parametros(Set.copyOf(versaoDaOrdem.values()));
		var fases = servicos.fasesComFoto(Set.copyOf(versaoDaOrdem.values()));
		var motivos = servicos.motivosParaOrdem().stream().filter(m -> MOTIVOS_DO_CAMPO.contains(m.tipo())).toList();
		var jornada = jornadas.abertas(List.of(equipe)).stream().findFirst().orElse(null);
		var nome = equipes.buscar(equipe).orElseThrow(RecursoNaoEncontradoException::new).nome();
		var avisos = contatos.situacoes(versaoDaOrdem.keySet());
		var semContato = new ContatoRepository.SituacaoDoAviso(false, null);
		return new PacoteDoDia(versao, desde != null, equipe, nome, jornada,
				noPacote.stream().map(OrdemServicoRepository.NoPacote::id).sorted().toList(),
				detalhes.stream().map(o -> {
					var aviso = avisos.getOrDefault(o.id(), semContato);
					return new PacoteDoDia.OrdemDoPacote(o, versaoDaOrdem.get(o.id()), sequencias.get(o.id()),
							aviso.temContato(), aviso.avisadoEm(),
							fases.getOrDefault(versaoDaOrdem.get(o.id()), FaseComFoto.TODAS));
				}).toList(), parametros, motivos, execucao.raioDoInicio(), agora);
	}

	/**
	 * RN-27: posições da jornada mais recente das equipes da pessoa; as de fora da
	 * jornada são descartadas.
	 * @return quantas foram gravadas
	 */
	@Transactional
	public int registrarPosicoes(@Nullable UUID dispositivoId, List<AmostraDePosicao> amostras) {
		dispositivos.exigir(dispositivoId, false);
		if (amostras.size() > MAXIMO_DE_POSICOES) {
			throw new RegraVioladaException("Envie até " + MAXIMO_DE_POSICOES + " posições por vez.");
		}
		var jornada = jornadas.ultima(equipes.equipesDe(OrdemServicoService.usuario()))
			.orElseThrow(() -> new ConflitoException("Posições só são registradas com a jornada aberta."));
		var ordenadas = amostras.stream().sorted(Comparator.comparing(AmostraDePosicao::momento)).toList();
		return jornadas.registrarPosicoes(jornada, ordenadas, agora().plus(ADIANTAMENTO_ACEITO));
	}

	private static UUID unica(List<UUID> equipes) {
		if (equipes.isEmpty()) {
			throw new RegraVioladaException("Você não faz parte de nenhuma equipe.");
		}
		if (equipes.size() > 1) {
			throw new RegraVioladaException("Você está em mais de uma equipe: escolha a equipe da jornada.");
		}
		return equipes.getFirst();
	}

	private Instant agora() {
		return relogio.instant();
	}

}
