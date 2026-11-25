package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.EventoDeCampo;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.RespostaDoEvento;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.SituacaoEvento;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.model.TipoConflito;
import dev.aurora.campanhas.model.ValoresDoEncerramento;
import dev.aurora.campanhas.repository.ConflitoRepository;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.campanhas.repository.ExecucaoRepository;
import dev.aurora.campanhas.repository.ExecucaoRepository.EventoGravado;
import dev.aurora.campanhas.repository.ExecucaoRepository.OrdemNoCampo;
import dev.aurora.campanhas.repository.JornadaRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.Servicos;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Eventos do app de campo (RN-22, RN-26; doc 10, 10.6, 10.9.4 e 10.9.5; ADR 0045). Cada
 * evento é aplicado uma única vez (pelo id gerado no celular) e na ordem da sequência da
 * OS; o que chega antes do anterior espera. Quando a programação mudou sem o agente
 * saber, vale o que o campo fez e o programador recebe um conflito; o que não é trabalho
 * de campo (devolver) dá lugar à programação.
 */
@Service
public class ExecucaoService {

	static final int MAXIMO_POR_LOTE = 200;

	/** RN-26: diferença do relógio do celular que gera aviso. */
	static final Duration TOLERANCIA_DO_RELOGIO = Duration.ofMinutes(5);

	/** Evento com hora mais adiantada que isso é recusado (relógio muito errado). */
	static final Duration FUTURO_MAXIMO = Duration.ofDays(1);

	private final ExecucaoRepository execucao;

	private final ConflitoRepository conflitos;

	private final JornadaRepository jornadas;

	private final EquipeRepository equipes;

	private final OrdemServicoRepository ordens;

	private final DispositivoService dispositivos;

	private final MotivosDaOrdem motivos;

	private final Servicos servicos;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final TransactionTemplate transacao;

	ExecucaoService(ExecucaoRepository execucao, ConflitoRepository conflitos, JornadaRepository jornadas,
			EquipeRepository equipes, OrdemServicoRepository ordens, DispositivoService dispositivos,
			MotivosDaOrdem motivos, Servicos servicos, Auditoria auditoria, Clock relogio,
			TransactionTemplate transacao) {
		this.execucao = execucao;
		this.conflitos = conflitos;
		this.jornadas = jornadas;
		this.equipes = equipes;
		this.ordens = ordens;
		this.dispositivos = dispositivos;
		this.motivos = motivos;
		this.servicos = servicos;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.transacao = transacao;
	}

	/**
	 * @param revogado o celular foi bloqueado: o app apaga os dados locais depois deste
	 * envio
	 */
	public record Recebimento(List<RespostaDoEvento> eventos, boolean revogado) {

		public Recebimento {
			eventos = List.copyOf(eventos);
		}

	}

	/**
	 * Recebe um lote de eventos, na ordem enviada; cada evento em transação própria (um
	 * evento recusado não desfaz os outros).
	 * @param enviadoEm relógio do celular no envio (para o aviso de relógio)
	 */
	public Recebimento receber(@Nullable UUID dispositivoId, @Nullable Instant enviadoEm, List<EventoDeCampo> eventos) {
		if (eventos.isEmpty() || eventos.size() > MAXIMO_POR_LOTE) {
			throw new RegraVioladaException("Envie de 1 a " + MAXIMO_POR_LOTE + " eventos por vez.");
		}
		var dispositivo = Objects.requireNonNull(transacao.execute(s -> dispositivos.exigir(dispositivoId, true)));
		var usuario = OrdemServicoService.usuario();
		var agora = relogio.instant();
		var avisos = new ArrayList<String>();
		if (enviadoEm != null && Duration.between(enviadoEm, agora).abs().compareTo(TOLERANCIA_DO_RELOGIO) > 0) {
			avisos.add("RELOGIO");
		}
		if (dispositivo.revogado()) {
			avisos.add("APARELHO_REVOGADO");
		}
		var respostas = new ArrayList<RespostaDoEvento>();
		for (var evento : eventos) {
			respostas.add(receberUm(evento, usuario, dispositivo.id(), avisos, agora));
		}
		return new Recebimento(respostas, dispositivo.revogado());
	}

	private RespostaDoEvento receberUm(EventoDeCampo evento, UUID usuario, UUID dispositivo, List<String> avisos,
			Instant agora) {
		try {
			return Objects.requireNonNull(
					transacao.execute(s -> receberNaTransacao(evento, usuario, dispositivo, avisos, agora)));
		}
		catch (DuplicateKeyException corrida) {
			// O mesmo evento chegou ao mesmo tempo por outra conexão.
			return Objects.requireNonNull(transacao.execute(s -> repetido(execucao.evento(evento.id()).orElseThrow())));
		}
	}

	private RespostaDoEvento receberNaTransacao(EventoDeCampo e, UUID usuario, UUID dispositivo,
			List<String> avisosDoLote, Instant agora) {
		var existente = execucao.evento(e.id());
		if (existente.isPresent()) {
			return repetido(existente.get());
		}
		var os = execucao.travar(e.osId()).orElse(null);
		if (os == null) {
			return rejeitado(e, "OS não encontrada.");
		}
		var minhasEquipes = equipes.equipesDe(usuario);
		if (minhasEquipes.isEmpty()) {
			return rejeitado(e, "Você não faz parte de nenhuma equipe.");
		}
		var equipe = os.equipeId() != null && minhasEquipes.contains(os.equipeId()) ? os.equipeId()
				: jornadas.abertas(minhasEquipes)
					.stream()
					.map(j -> j.equipeId())
					.findFirst()
					.orElse(minhasEquipes.getFirst());
		var avisos = new ArrayList<>(avisosDoLote);
		String recusa = null;
		if (e.sequencia() <= 0) {
			recusa = "Sequência inválida.";
		}
		else if (e.ocorridoEm().isAfter(agora.plus(FUTURO_MAXIMO))) {
			recusa = "A hora do evento está no futuro; acerte o relógio do celular.";
		}
		else if (e.sequencia() <= os.ultimaSequencia() || execucao.sequenciaOcupada(os.id(), e.sequencia())) {
			recusa = "A sequência " + e.sequencia() + " já foi usada nesta OS.";
		}
		if (recusa != null) {
			execucao.inserir(e, os.unidadeId(), SituacaoEvento.REJEITADO, recusa, avisos, equipe, usuario, dispositivo);
			return new RespostaDoEvento(e.id(), SituacaoEvento.REJEITADO, null, recusa, avisos);
		}
		execucao.inserir(e, os.unidadeId(), SituacaoEvento.AGUARDANDO, null, avisos, equipe, usuario, dispositivo);
		if (e.sequencia() > os.ultimaSequencia() + 1) {
			var espera = "Aguardando o evento " + (os.ultimaSequencia() + 1) + " desta OS.";
			execucao.processado(e.id(), SituacaoEvento.AGUARDANDO, espera, avisos, null, null, null);
			return new RespostaDoEvento(e.id(), SituacaoEvento.AGUARDANDO, null, espera, avisos);
		}
		var resposta = processar(execucao.evento(e.id()).orElseThrow(), os);
		// Os que esperavam por este, em sequência.
		var sequencia = e.sequencia();
		var proximo = execucao.aguardando(os.id(), sequencia + 1);
		while (proximo.isPresent()) {
			processar(proximo.get(), execucao.travar(os.id()).orElseThrow());
			sequencia++;
			proximo = execucao.aguardando(os.id(), sequencia + 1);
		}
		return resposta;
	}

	private RespostaDoEvento repetido(EventoGravado gravado) {
		return new RespostaDoEvento(gravado.evento().id(), SituacaoEvento.REPETIDO, gravado.situacao(),
				gravado.mensagem(), gravado.avisos());
	}

	/** Recusa sem gravar (não há OS visível ou equipe para registrar). */
	private static RespostaDoEvento rejeitado(EventoDeCampo e, String mensagem) {
		return new RespostaDoEvento(e.id(), SituacaoEvento.REJEITADO, null, mensagem, List.of());
	}

	/** O que aconteceu com o evento. */
	private record Desfecho(SituacaoEvento situacao, @Nullable String mensagem, @Nullable Double distancia,
			@Nullable JsonNode valores) {

		static Desfecho de(SituacaoEvento situacao, String mensagem) {
			return new Desfecho(situacao, mensagem, null, null);
		}

	}

	private RespostaDoEvento processar(EventoGravado gravado, OrdemNoCampo os) {
		var e = gravado.evento();
		var avisos = new ArrayList<>(gravado.avisos());
		var jornada = jornadas.daEquipeEm(gravado.equipeId(), e.ocorridoEm()).orElse(null);
		if (jornada == null && e.tipo().trabalhoDeCampo()) {
			avisos.add("SEM_JORNADA");
		}
		Desfecho desfecho;
		try {
			desfecho = decidir(e, os, gravado.equipeId(), gravado.usuarioId(), avisos);
		}
		catch (RegraVioladaException erro) {
			desfecho = Desfecho.de(SituacaoEvento.REJEITADO, erro.getMessage());
		}
		execucao.processado(e.id(), desfecho.situacao(), desfecho.mensagem(), avisos, desfecho.distancia(), jornada,
				desfecho.valores());
		execucao.avancarSequencia(os.id(), e.sequencia());
		return new RespostaDoEvento(e.id(), desfecho.situacao(), null, desfecho.mensagem(), avisos);
	}

	private Desfecho decidir(EventoDeCampo e, OrdemNoCampo os, UUID equipe, UUID usuario, List<String> avisos) {
		var campo = e.tipo().trabalhoDeCampo();
		if (os.status() == StatusOrdem.ENCERRADA) {
			if (!campo) {
				return Desfecho.de(SituacaoEvento.SUPERADO, "A OS já foi encerrada.");
			}
			var validado = validar(e, os);
			abrirConflito(os, e, TipoConflito.JA_ENCERRADA,
					"A OS já estava encerrada e a equipe também registrou " + acao(e) + ".");
			return new Desfecho(SituacaoEvento.CONFLITO,
					"A OS já estava encerrada; o registro ficou para o programador.", null,
					validado.valores() == null ? null : validado.valores().valores());
		}
		var cancelada = os.status() == StatusOrdem.CANCELADA;
		if (cancelada || !equipe.equals(os.equipeId())) {
			var tipo = cancelada ? TipoConflito.OS_CANCELADA : TipoConflito.OUTRA_EQUIPE;
			if (!campo) {
				return Desfecho.de(SituacaoEvento.SUPERADO,
						cancelada ? "A OS foi cancelada pela programação." : "A OS passou para outra equipe.");
			}
			var validado = validar(e, os);
			var assumido = switch (os.status()) {
				case CANCELADA, PLANEJADA, DEVOLVIDA -> StatusOrdem.PROGRAMADA;
				default -> os.status();
			};
			var destino = e.tipo().destino(assumido);
			if ((cancelada && execucao.substituida(os.id())) || destino.isEmpty()) {
				abrirConflito(os, e, tipo,
						(cancelada ? "A OS foi cancelada e substituída" : "A OS está com outra equipe")
								+ " e esta equipe registrou " + acao(e) + ".");
				return new Desfecho(SituacaoEvento.CONFLITO,
						"A programação mudou; o registro ficou para o programador.", null,
						validado.valores() == null ? null : validado.valores().valores());
			}
			// Vale o campo: a OS fica com a equipe que trabalhou nela.
			execucao.assumir(os.id(), equipe, LocalDate.ofInstant(e.ocorridoEm(), relogio.getZone()));
			abrirConflito(os, e, tipo,
					(cancelada ? "A OS tinha sido cancelada" : "A OS tinha passado para outra equipe")
							+ " e esta equipe registrou " + acao(e) + "; ficou com ela.");
			var atual = execucao.travar(os.id()).orElseThrow();
			var desfecho = efetivar(e, atual, destino.get(), validado, equipe, usuario, avisos);
			return new Desfecho(SituacaoEvento.CONFLITO,
					"Registrado; a programação tinha mudado e o programador foi avisado.", desfecho.distancia(),
					desfecho.valores());
		}
		var destino = e.tipo()
			.destino(os.status())
			.orElseThrow(() -> new RegraVioladaException(
					"Não é possível " + e.tipo().verbo() + " uma OS " + rotulo(os.status()) + "."));
		return efetivar(e, os, destino, validar(e, os), equipe, usuario, avisos);
	}

	/** Motivo, observação, valores e quantidade conferidos antes de mudar a OS. */
	private record Validado(@Nullable UUID motivo, @Nullable String observacao, @Nullable ValoresDoEncerramento valores,
			@Nullable BigDecimal quantidade) {
	}

	private Validado validar(EventoDeCampo e, OrdemNoCampo os) {
		return switch (e.tipo()) {
			case INICIAR, RETOMAR -> new Validado(null, MotivosDaOrdem.observacao(e.observacao()), null, null);
			case PARALISAR -> {
				var j = motivos.validar(e.motivoId(), "PARALISACAO", os.servicoId(), e.observacao(),
						"Informe o motivo da paralisação.", true);
				yield new Validado(j.motivoId(), j.observacao(), null, null);
			}
			case DEVOLVER -> {
				var j = motivos.validar(e.motivoId(), "DEVOLUCAO", os.servicoId(), e.observacao(),
						"Informe o motivo da devolução.", true);
				yield new Validado(j.motivoId(), j.observacao(), null, null);
			}
			case ENCERRAR -> {
				if (e.resultado() == null) {
					throw new RegraVioladaException("Informe se a OS foi executada.");
				}
				if (e.resultado() == ResultadoOrdem.NAO_EXECUTADA) {
					var j = motivos.validar(e.motivoId(), "NAO_EXECUCAO", os.servicoId(), e.observacao(),
							"Informe por que a OS não foi executada.", true);
					yield new Validado(j.motivoId(), j.observacao(), null, BigDecimal.ZERO);
				}
				var parametros = servicos.parametros(List.of(os.servicoVersaoId()))
					.getOrDefault(os.servicoVersaoId(), List.of());
				var valores = ValoresDoEncerramento.validar(parametros, e.valores());
				var quantidade = OrdemServicoService.quantidade(
						new RegistroResultado(ResultadoOrdem.EXECUTADA, null, null, e.quantidade()),
						os.quantidadePrevista());
				yield new Validado(null, MotivosDaOrdem.observacao(e.observacao()), valores, quantidade);
			}
		};
	}

	private Desfecho efetivar(EventoDeCampo e, OrdemNoCampo os, StatusOrdem destino, Validado v, UUID equipe,
			UUID usuario, List<String> avisos) {
		Double distancia = null;
		switch (e.tipo()) {
			case INICIAR -> {
				if (e.posicao() == null) {
					avisos.add("SEM_GPS");
				}
				else {
					distancia = execucao.distanciaAoAlvo(os.id(), e.posicao()).orElse(null);
					if (distancia != null && distancia > execucao.raioDoInicio()) {
						avisos.add("FORA_DO_LOCAL");
						execucao.marcarForaDoLocal(os.id());
					}
				}
				if (execucao.outraEmExecucao(equipe, os.id())) {
					avisos.add("EXECUCAO_SIMULTANEA");
				}
				execucao.mover(os.id(), destino, null, v.observacao(), e.ocorridoEm(), usuario);
			}
			case RETOMAR, PARALISAR ->
				execucao.mover(os.id(), destino, v.motivo(), v.observacao(), e.ocorridoEm(), usuario);
			case DEVOLVER -> {
				execucao.mover(os.id(), destino, v.motivo(), v.observacao(), e.ocorridoEm(), usuario);
				var detalhes = new LinkedHashMap<String, @Nullable Object>();
				detalhes.put("equipe", equipe.toString());
				detalhes.put("servico", os.servicoCodigo());
				detalhes.put("motivo", String.valueOf(v.motivo()));
				detalhes.put("evento", e.id().toString());
				auditoria.registrar(TipoEvento.ORDEM_DEVOLVIDA, "ordem-de-servico/" + os.id(), detalhes);
			}
			case ENCERRAR -> encerrar(e, os, v, usuario);
		}
		return new Desfecho(SituacaoEvento.APLICADO, null, distancia,
				v.valores() == null ? null : v.valores().valores());
	}

	private void encerrar(EventoDeCampo e, OrdemNoCampo os, Validado v, UUID usuario) {
		var resultado = e.resultado() == null ? ResultadoOrdem.EXECUTADA : e.resultado();
		var executada = resultado == ResultadoOrdem.EXECUTADA;
		var conferencia = executada && os.conferidoPelaCarga() ? Conferencia.PENDENTE : Conferencia.NAO_SE_APLICA;
		var quantidade = v.quantidade() == null ? BigDecimal.ZERO : v.quantidade();
		execucao.encerrar(os.id(), resultado, v.motivo(), v.observacao(), quantidade,
				v.valores() == null ? null : v.valores().valores(), e.ocorridoEm(), usuario, conferencia);
		var localizado = executada && os.matricula() != null && e.posicao() != null
				&& execucao.localizarImovelPeloCampo(os.matricula(), e.posicao(), usuario);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("campanha", os.campanhaId() == null ? null : os.campanhaId().toString());
		detalhes.put("servico", os.servicoCodigo());
		detalhes.put("resultado", resultado.name());
		detalhes.put("motivo", v.motivo() == null ? null : v.motivo().toString());
		detalhes.put("quantidade", quantidade.toPlainString());
		detalhes.put("registradoPor", "APP");
		detalhes.put("evento", e.id().toString());
		detalhes.put("imovelLocalizado", localizado);
		auditoria.registrar(TipoEvento.RESULTADO_DE_ORDEM_REGISTRADO, "ordem-de-servico/" + os.id(), detalhes);
		if (conferencia == Conferencia.PENDENTE) {
			ordens.conferir(relogio.getZone().getId());
		}
	}

	private void abrirConflito(OrdemNoCampo os, EventoDeCampo e, TipoConflito tipo, String descricao) {
		if (conflitos.abrir(os.id(), os.unidadeId(), e.id(), tipo, descricao)) {
			var detalhes = new LinkedHashMap<String, @Nullable Object>();
			detalhes.put("tipo", tipo.name());
			detalhes.put("evento", e.id().toString());
			detalhes.put("acao", e.tipo().name());
			auditoria.registrar(TipoEvento.CONFLITO_DE_CAMPO_ABERTO, "ordem-de-servico/" + os.id(), detalhes);
		}
	}

	private static String acao(EventoDeCampo e) {
		return switch (e.tipo()) {
			case INICIAR -> "o início";
			case PARALISAR -> "uma paralisação";
			case RETOMAR -> "a retomada";
			case DEVOLVER -> "a devolução";
			case ENCERRAR -> e.resultado() == ResultadoOrdem.NAO_EXECUTADA ? "a não execução" : "a execução";
		};
	}

	static String rotulo(StatusOrdem status) {
		return switch (status) {
			case PLANEJADA -> "planejada";
			case PROGRAMADA -> "programada";
			case EM_EXECUCAO -> "em execução";
			case PARALISADA -> "paralisada";
			case DEVOLVIDA -> "devolvida";
			case ENCERRADA -> "encerrada";
			case CANCELADA -> "cancelada";
		};
	}

}
