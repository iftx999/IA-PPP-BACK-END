package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.FaixaDoLote;
import dev.aurora.campanhas.model.FiltroOrdens;
import dev.aurora.campanhas.model.Ignorada;
import dev.aurora.campanhas.model.Justificativa;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.PaginaOrdens;
import dev.aurora.campanhas.model.PreviaLote;
import dev.aurora.campanhas.model.Prioridade;
import dev.aurora.campanhas.model.Programacao;
import dev.aurora.campanhas.model.ResultadoCriacao;
import dev.aurora.campanhas.model.ResultadoDistribuicao;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository.NovasOrdens;
import dev.aurora.campanhas.repository.OrdemServicoRepository.Origem;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.MotivoDaOrdem;
import dev.aurora.servicos.ServicoDaOrdem;
import dev.aurora.servicos.Servicos;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programação das OS (J4, J5; doc 10, 10.6; ADR 0044): OS avulsas por imóvel, OS de
 * trecho, lote por logradouro, programar e reprogramar para uma equipe numa data, e
 * cancelar. Prazo pelo serviço e pela prioridade (RN-21).
 */
@Service
public class ProgramacaoService {

	/** Imóveis ou trechos por pedido de OS avulsa. */
	static final int LIMITE_AVULSAS = 500;

	/** RN-24: máximo de OS por lote (o mesmo das campanhas) e por programação. */
	static final int LIMITE_DO_LOTE = CampanhaService.LIMITE_DE_ORDENS;

	/** Programação a até 180 dias. */
	static final int PROGRAMACAO_MAXIMA_EM_DIAS = 180;

	static final int INSTRUCOES_MAXIMAS = 500;

	/** Ignoradas devolvidas na resposta (o total vem à parte). */
	static final int IGNORADAS_NA_RESPOSTA = 200;

	static final String JA_TEM_OS = "Já tem OS aberta deste serviço";

	private final OrdemServicoRepository ordens;

	private final EquipeRepository equipes;

	private final Servicos servicos;

	private final MotivosDaOrdem motivos;

	private final DadosPessoaisDasOrdens dadosPessoais;

	private final Auditoria auditoria;

	private final Clock relogio;

	ProgramacaoService(OrdemServicoRepository ordens, EquipeRepository equipes, Servicos servicos,
			MotivosDaOrdem motivos, DadosPessoaisDasOrdens dadosPessoais, Auditoria auditoria, Clock relogio) {
		this.ordens = ordens;
		this.equipes = equipes;
		this.servicos = servicos;
		this.motivos = motivos;
		this.dadosPessoais = dadosPessoais;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** Serviços ativos e motivos para os formulários da programação. */
	public record Opcoes(List<ServicoDaOrdem> servicos, List<MotivoDaOrdem> motivos) {
	}

	@Transactional(readOnly = true)
	public Opcoes opcoes() {
		return new Opcoes(servicos.servicosParaOrdem(), servicos.motivosParaOrdem());
	}

	@Transactional
	public PaginaOrdens listar(FiltroOrdens filtro, int pagina, int tamanho) {
		var agora = relogio.instant();
		var itens = dadosPessoais.aplicar(ordens.listar(filtro, agora, pagina, tamanho), "Lista de ordens de serviço");
		return new PaginaOrdens(itens, ordens.contar(filtro, agora), pagina, tamanho);
	}

	@Transactional
	public OrdemServico buscar(UUID id) {
		var ordem = ordens.buscar(id, relogio.instant()).orElseThrow(RecursoNaoEncontradoException::new);
		return dadosPessoais.aplicar(List.of(ordem), "Detalhe de ordem de serviço").getFirst();
	}

	// ----- Criação -----

	/** J4: uma OS do serviço para cada imóvel (matrícula). */
	@Transactional
	public ResultadoCriacao criarParaImoveis(UUID servicoId, List<String> matriculas, Prioridade prioridade,
			@Nullable String instrucoes) {
		var servico = servicoAtivo(servicoId);
		if (!servico.aceitaImovel()) {
			throw new RegraVioladaException(
					"O serviço " + servico.nome() + " é feito em trecho de rede, não em imóvel.");
		}
		var pedidas = new LinkedHashSet<String>();
		matriculas.stream().map(String::strip).filter(m -> !m.isEmpty()).forEach(pedidas::add);
		if (pedidas.isEmpty() || pedidas.size() > LIMITE_AVULSAS) {
			throw new RegraVioladaException("Informe de 1 a " + LIMITE_AVULSAS + " imóveis.");
		}
		var texto = instrucoes(instrucoes);
		var encontrados = new HashMap<String, OrdemServicoRepository.ImovelAlvo>();
		ordens.imoveis(pedidas).forEach(i -> encontrados.put(i.matricula(), i));
		var ignoradas = new ArrayList<Ignorada>();
		var novas = new Lista();
		var prazos = new Prazos(servico, prioridade);
		for (var matricula : pedidas) {
			var imovel = encontrados.get(matricula);
			if (imovel == null) {
				ignoradas.add(new Ignorada(matricula, "Imóvel não encontrado na sua área"));
				continue;
			}
			novas.imovel(matricula, imovel.unidadeId(), servico, imovel.situacao(), prioridade,
					prazos.para(imovel.unidadeId()), BigDecimal.ONE);
		}
		var criadas = new HashSet<>(
				ordens.inserir(novas.ordens(), new Origem(null, null, texto, OrdemServicoService.usuario())));
		novas.alvos()
			.stream()
			.filter(a -> !criadas.contains(a))
			.forEach(a -> ignoradas.add(new Ignorada(a, JA_TEM_OS)));
		auditarCriacao("ordens-de-servico", servico, prioridade, "IMOVEL", criadas.size(), ignoradas.size(), null);
		return resultado(null, criadas.size(), ignoradas);
	}

	/**
	 * J5: uma OS do serviço para cada trecho; quantidade prevista = extensão quando o
	 * serviço é medido em metros.
	 */
	@Transactional
	public ResultadoCriacao criarParaTrechos(UUID servicoId, List<UUID> trechos, Prioridade prioridade,
			@Nullable String instrucoes) {
		var servico = servicoAtivo(servicoId);
		if (!servico.aceitaTrecho()) {
			throw new RegraVioladaException(
					"O serviço " + servico.nome() + " é feito em imóvel, não em trecho de rede.");
		}
		var pedidos = new LinkedHashSet<>(trechos);
		if (pedidos.isEmpty() || pedidos.size() > LIMITE_AVULSAS) {
			throw new RegraVioladaException("Escolha de 1 a " + LIMITE_AVULSAS + " trechos.");
		}
		var texto = instrucoes(instrucoes);
		var encontrados = ordens.trechos(pedidos);
		var ignoradas = new ArrayList<Ignorada>();
		var achados = new HashSet<>(encontrados.stream().map(OrdemServicoRepository.TrechoAlvo::id).toList());
		pedidos.stream()
			.filter(t -> !achados.contains(t))
			.forEach(t -> ignoradas.add(new Ignorada(t.toString(), "Trecho não encontrado na sua área")));
		var novas = new Lista();
		var prazos = new Prazos(servico, prioridade);
		var codigos = new HashMap<String, String>();
		for (var trecho : encontrados) {
			codigos.put(trecho.id().toString(), trecho.codigo());
			novas.trecho(trecho.id(), trecho.unidadeId(), servico, prioridade, prazos.para(trecho.unidadeId()),
					quantidadeDoTrecho(servico, trecho.extensaoM()));
		}
		var criadas = new HashSet<>(
				ordens.inserir(novas.ordens(), new Origem(null, null, texto, OrdemServicoService.usuario())));
		novas.alvos()
			.stream()
			.filter(a -> !criadas.contains(a))
			.forEach(a -> ignoradas.add(new Ignorada(codigos.getOrDefault(a, a), JA_TEM_OS)));
		auditarCriacao("ordens-de-servico", servico, prioridade, "TRECHO", criadas.size(), ignoradas.size(), null);
		return resultado(null, criadas.size(), ignoradas);
	}

	/**
	 * Extensão do trecho quando o serviço é por metro (mínimo 0,01); senão uma unidade.
	 */
	static BigDecimal quantidadeDoTrecho(ServicoDaOrdem servico, BigDecimal extensao) {
		if (!"M".equals(servico.unidadeMedida())) {
			return BigDecimal.ONE;
		}
		var metros = extensao.setScale(2, RoundingMode.HALF_UP);
		return metros.signum() > 0 ? metros : new BigDecimal("0.01");
	}

	/** J5: quantos imóveis do logradouro (ou da faixa) entram no lote. */
	@Transactional(readOnly = true)
	public PreviaLote previaLote(FaixaDoLote faixa, UUID servicoId) {
		validar(faixa);
		var servico = servicoDoLote(servicoId);
		var logradouro = ordens.logradouro(faixa.logradouroId()).orElseThrow(RecursoNaoEncontradoException::new);
		var imoveis = ordens.imoveisDoLote(faixa, servico.id());
		var comOs = imoveis.stream().filter(OrdemServicoRepository.ImovelDoLote::comOsAberta).count();
		return new PreviaLote(logradouro.id(), logradouro.nome(), logradouro.unidadeId(), imoveis.size(), comOs,
				imoveis.size() - comOs, LIMITE_DO_LOTE);
	}

	/**
	 * J5, RN-24: uma OS por imóvel do logradouro (ou da faixa), na ordem da numeração; os
	 * que já têm OS aberta do serviço ficam de fora e são informados.
	 */
	@Transactional
	public ResultadoCriacao criarLote(FaixaDoLote faixa, UUID servicoId, Prioridade prioridade,
			@Nullable String instrucoes) {
		validar(faixa);
		var servico = servicoDoLote(servicoId);
		var texto = instrucoes(instrucoes);
		var logradouro = ordens.logradouro(faixa.logradouroId()).orElseThrow(RecursoNaoEncontradoException::new);
		var imoveis = ordens.imoveisDoLote(faixa, servico.id());
		var ignoradas = new ArrayList<Ignorada>();
		imoveis.stream()
			.filter(OrdemServicoRepository.ImovelDoLote::comOsAberta)
			.forEach(i -> ignoradas.add(new Ignorada(i.matricula(), JA_TEM_OS)));
		var novos = imoveis.stream().filter(i -> !i.comOsAberta()).toList();
		if (novos.isEmpty()) {
			throw new RegraVioladaException(imoveis.isEmpty() ? "Nenhum imóvel vinculado a este logradouro ou faixa."
					: "Todos os imóveis da faixa já têm OS aberta deste serviço.");
		}
		if (novos.size() > LIMITE_DO_LOTE) {
			throw new RegraVioladaException("A faixa tem " + novos.size() + " imóveis; o limite por lote é "
					+ LIMITE_DO_LOTE + ". Divida por faixa de numeração.");
		}
		var usuario = OrdemServicoService.usuario();
		var lote = UUID.randomUUID();
		ordens.inserirLote(lote, logradouro, servico.id(), servico.versaoId(), faixa, prioridade, usuario);
		var novas = new Lista();
		var prazos = new Prazos(servico, prioridade);
		for (var imovel : novos) {
			novas.imovel(imovel.matricula(), imovel.unidadeId(), servico, null, prioridade,
					prazos.para(imovel.unidadeId()), BigDecimal.ONE);
		}
		var criadas = new HashSet<>(ordens.inserir(novas.ordens(), new Origem(null, lote, texto, usuario)));
		novas.alvos()
			.stream()
			.filter(a -> !criadas.contains(a))
			.forEach(a -> ignoradas.add(new Ignorada(a, JA_TEM_OS)));
		ordens.contarLote(lote, criadas.size(), ignoradas.size());
		auditarCriacao("lote-os/" + lote, servico, prioridade, "LOTE", criadas.size(), ignoradas.size(),
				logradouro.id());
		return resultado(lote, criadas.size(), ignoradas);
	}

	private static void validar(FaixaDoLote faixa) {
		if (faixa.numeroInicial() != null && faixa.numeroFinal() != null
				&& faixa.numeroInicial() > faixa.numeroFinal()) {
			throw new RegraVioladaException("O número inicial deve ser menor ou igual ao final.");
		}
	}

	private ServicoDaOrdem servicoDoLote(UUID servicoId) {
		var servico = servicoAtivo(servicoId);
		if (!servico.aceitaImovel()) {
			throw new RegraVioladaException("Lote é para serviço em imóvel; para rede, crie OS de trecho.");
		}
		return servico;
	}

	// ----- Programar e cancelar -----

	/**
	 * Programa (ou reprograma) as OS para a equipe na data. Só vão as que estão
	 * planejadas, programadas ou devolvidas e cujo alvo fica na área da equipe (RN-23).
	 */
	@Transactional
	public ResultadoDistribuicao programar(Programacao pedido) {
		return programar(pedido, null);
	}

	/** {@code campanha} não nula restringe às OS dela (envio pela tela da campanha). */
	ResultadoDistribuicao programar(Programacao pedido, @Nullable UUID campanha) {
		var equipe = equipes.buscar(pedido.equipeId())
			.orElseThrow(() -> new RegraVioladaException("Escolha uma equipe da sua área."));
		if (!equipe.ativa()) {
			throw new RegraVioladaException("A equipe está desativada.");
		}
		var hoje = LocalDate.now(relogio);
		if (pedido.data().isBefore(hoje) || pedido.data().isAfter(hoje.plusDays(PROGRAMACAO_MAXIMA_EM_DIAS))) {
			throw new RegraVioladaException(
					"A data deve ficar entre hoje e daqui a " + PROGRAMACAO_MAXIMA_EM_DIAS + " dias.");
		}
		var lista = List.copyOf(new LinkedHashSet<>(pedido.ordens()));
		if (lista.isEmpty() || lista.size() > LIMITE_DO_LOTE) {
			throw new RegraVioladaException("Escolha de 1 a " + LIMITE_DO_LOTE + " OS.");
		}
		var envio = ordens.programar(lista, campanha, equipe.id(), pedido.data(), OrdemServicoService.usuario());
		if (envio.enviadas() > 0) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("equipe", equipe.id().toString());
			detalhes.put("equipeNome", equipe.nome());
			detalhes.put("data", pedido.data().toString());
			detalhes.put("programadas", envio.enviadas());
			detalhes.put("foraDaArea", envio.foraDaArea());
			detalhes.put("incompativeis", envio.incompativeis());
			detalhes.put("ignoradas", envio.ignoradas());
			auditoria.registrar(TipoEvento.ORDENS_PROGRAMADAS,
					campanha == null ? "ordens-de-servico" : "campanha/" + campanha, detalhes);
		}
		return new ResultadoDistribuicao(envio.enviadas(), envio.foraDaArea(), envio.incompativeis(),
				envio.ignoradas());
	}

	/** Cancela a OS que ainda não começou (planejada, programada ou devolvida). */
	@Transactional
	public OrdemServico cancelar(UUID id, Justificativa pedido) {
		var travada = ordens.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!travada.status().cancelavel()) {
			throw new ConflitoException(travada.status().aberta()
					? "A OS já está com a equipe em execução; peça a devolução antes de cancelar."
					: "Esta OS já foi encerrada ou cancelada.");
		}
		var justificativa = motivos.validar(pedido.motivoId(), "CANCELAMENTO", travada.servicoId(), pedido.observacao(),
				"Informe o motivo do cancelamento.");
		ordens.movimentar(id, StatusOrdem.CANCELADA, justificativa.motivoId(), justificativa.observacao(),
				OrdemServicoService.usuario());
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("servico", travada.servicoCodigo());
		detalhes.put("statusAnterior", travada.status().name());
		detalhes.put("motivo", justificativa.motivoId().toString());
		auditoria.registrar(TipoEvento.ORDEM_CANCELADA, "ordem-de-servico/" + id, detalhes);
		return buscar(id);
	}

	// ----- Apoio -----

	private ServicoDaOrdem servicoAtivo(UUID id) {
		var servico = servicos.servicoParaOrdem(id)
			.orElseThrow(() -> new RegraVioladaException("Escolha um serviço do catálogo."));
		if (!servico.ativo()) {
			throw new RegraVioladaException("O serviço " + servico.nome() + " está inativo e não aceita OS nova.");
		}
		return servico;
	}

	static @Nullable String instrucoes(@Nullable String texto) {
		var limpo = texto == null || texto.isBlank() ? null : texto.strip();
		if (limpo != null && limpo.length() > INSTRUCOES_MAXIMAS) {
			throw new RegraVioladaException("As instruções têm no máximo " + INSTRUCOES_MAXIMAS + " caracteres.");
		}
		return limpo;
	}

	/**
	 * RN-21: emergencial = agora + horas corridas; demais = fim do dia útil de número
	 * {@code dias} (sem fins de semana e feriados da unidade), no fuso da operação.
	 */
	static Instant prazo(ServicoDaOrdem servico, Prioridade prioridade, Instant agora, ZoneId zona,
			BiFunction<LocalDate, Integer, LocalDate> somarDiasUteis) {
		var dias = switch (prioridade) {
			case EMERGENCIAL -> 0;
			case ALTA -> servico.prazoAltaDias();
			case MEDIA -> servico.prazoMediaDias();
			case BAIXA -> servico.prazoBaixaDias();
		};
		if (prioridade == Prioridade.EMERGENCIAL) {
			return agora.plus(Duration.ofHours(servico.prazoEmergencialHoras()));
		}
		var dia = somarDiasUteis.apply(LocalDate.ofInstant(agora, zona), dias);
		return dia.atTime(LocalTime.of(23, 59, 59)).atZone(zona).toInstant();
	}

	private void auditarCriacao(String alvo, ServicoDaOrdem servico, Prioridade prioridade, String tipo, int criadas,
			int ignoradas, @Nullable UUID logradouro) {
		if (criadas == 0) {
			return;
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("tipo", tipo);
		detalhes.put("servico", servico.codigo());
		detalhes.put("versao", servico.versao());
		detalhes.put("prioridade", prioridade.name());
		detalhes.put("criadas", criadas);
		detalhes.put("ignoradas", ignoradas);
		detalhes.put("logradouro", logradouro == null ? null : logradouro.toString());
		auditoria.registrar(TipoEvento.ORDENS_CRIADAS, alvo, detalhes);
	}

	private static ResultadoCriacao resultado(@Nullable UUID lote, int criadas, List<Ignorada> ignoradas) {
		return new ResultadoCriacao(lote, criadas,
				List.copyOf(ignoradas.subList(0, Math.min(ignoradas.size(), IGNORADAS_NA_RESPOSTA))), ignoradas.size());
	}

	/** Prazo por unidade (os feriados municipais mudam de uma para outra). */
	private final class Prazos {

		private final ServicoDaOrdem servico;

		private final Prioridade prioridade;

		private final Instant agora = relogio.instant();

		private final Map<UUID, Instant> porUnidade = new HashMap<>();

		Prazos(ServicoDaOrdem servico, Prioridade prioridade) {
			this.servico = servico;
			this.prioridade = prioridade;
		}

		Instant para(UUID unidade) {
			return porUnidade.computeIfAbsent(unidade, u -> prazo(servico, prioridade, agora, relogio.getZone(),
					(inicio, dias) -> servicos.somarDiasUteis(inicio, dias, u)));
		}

	}

	/** Listas paralelas das OS a criar. */
	private static final class Lista {

		private final List<@Nullable String> matriculas = new ArrayList<>();

		private final List<@Nullable UUID> trechos = new ArrayList<>();

		private final List<UUID> unidades = new ArrayList<>();

		private final List<UUID> servicos = new ArrayList<>();

		private final List<UUID> versoes = new ArrayList<>();

		private final List<@Nullable String> situacoes = new ArrayList<>();

		private final List<Prioridade> prioridades = new ArrayList<>();

		private final List<Instant> prazos = new ArrayList<>();

		private final List<BigDecimal> quantidades = new ArrayList<>();

		private final List<String> alvos = new ArrayList<>();

		void imovel(String matricula, UUID unidade, ServicoDaOrdem servico, @Nullable String situacao,
				Prioridade prioridade, Instant prazo, BigDecimal quantidade) {
			matriculas.add(matricula);
			trechos.add(null);
			alvos.add(matricula);
			comum(unidade, servico, situacao, prioridade, prazo, quantidade);
		}

		void trecho(UUID trecho, UUID unidade, ServicoDaOrdem servico, Prioridade prioridade, Instant prazo,
				BigDecimal quantidade) {
			matriculas.add(null);
			trechos.add(trecho);
			alvos.add(trecho.toString());
			comum(unidade, servico, null, prioridade, prazo, quantidade);
		}

		private void comum(UUID unidade, ServicoDaOrdem servico, @Nullable String situacao, Prioridade prioridade,
				Instant prazo, BigDecimal quantidade) {
			unidades.add(unidade);
			servicos.add(servico.id());
			versoes.add(servico.versaoId());
			situacoes.add(situacao);
			prioridades.add(prioridade);
			prazos.add(prazo);
			quantidades.add(quantidade);
		}

		List<String> alvos() {
			return alvos;
		}

		NovasOrdens ordens() {
			return new NovasOrdens(matriculas, trechos, unidades, servicos, versoes, situacoes, prioridades, prazos,
					quantidades);
		}

	}

}
