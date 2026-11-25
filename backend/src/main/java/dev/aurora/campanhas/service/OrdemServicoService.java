package dev.aurora.campanhas.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.CampanhaDivergente;
import dev.aurora.campanhas.Campanhas;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.Justificativa;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.ResultadoOrdem;
import dev.aurora.campanhas.model.StatusOrdem;
import dev.aurora.campanhas.repository.EquipeRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.contexto.ContextoAcesso;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordens de serviço no campo (doc 10, 10.6; ADR 0036, 0044) e a conferência pela carga
 * (RN-19). A equipe só vê e movimenta as OS das equipes de que faz parte; o gestor
 * encerra por qualquer equipe da sua área (ex.: empreiteira sem acesso à Aurora).
 * Iniciar, paralisar e retomar chegam com o app (M4).
 */
@Service
public class OrdemServicoService implements Campanhas {

	/** Encerradas e devolvidas recentes que continuam na lista da equipe. */
	static final Duration HISTORICO = Duration.ofDays(30);

	private final OrdemServicoRepository ordens;

	private final EquipeRepository equipes;

	private final DadosPessoaisDasOrdens dadosPessoais;

	private final MotivosDaOrdem motivos;

	private final Auditoria auditoria;

	private final Clock relogio;

	OrdemServicoService(OrdemServicoRepository ordens, EquipeRepository equipes, DadosPessoaisDasOrdens dadosPessoais,
			MotivosDaOrdem motivos, Auditoria auditoria, Clock relogio) {
		this.ordens = ordens;
		this.equipes = equipes;
		this.dadosPessoais = dadosPessoais;
		this.motivos = motivos;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** OS com as equipes da pessoa e as encerradas ou devolvidas nos últimos 30 dias. */
	@Transactional
	public List<OrdemServico> minhas() {
		var agora = relogio.instant();
		var lista = ordens.dasEquipes(equipes.equipesDe(usuario()), agora, agora.minus(HISTORICO));
		return dadosPessoais.aplicar(lista, "Ordens de serviço da equipe");
	}

	/** Encerramento informado por alguém da equipe da OS. */
	@Transactional
	public OrdemServico encerrarPelaEquipe(UUID id, RegistroResultado registro) {
		var travada = daEquipe(id);
		return encerrar(id, travada, registro, true);
	}

	/** Encerramento informado pelo gestor (ex.: planilha da empreiteira). */
	@Transactional
	public OrdemServico encerrarPeloGestor(UUID id, RegistroResultado registro) {
		var travada = ordens.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		return encerrar(id, travada, registro, false);
	}

	/** A equipe devolve a OS para a programação, com motivo (D8). */
	@Transactional
	public OrdemServico devolver(UUID id, Justificativa pedido) {
		var travada = daEquipe(id);
		if (!travada.status().devolvivel()) {
			throw new ConflitoException("Só OS programada, em execução ou paralisada pode ser devolvida.");
		}
		var justificativa = motivos.validar(pedido.motivoId(), "DEVOLUCAO", travada.servicoId(), pedido.observacao(),
				"Informe o motivo da devolução.");
		ordens.movimentar(id, StatusOrdem.DEVOLVIDA, justificativa.motivoId(), justificativa.observacao(), usuario());
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("equipe", travada.equipeId() == null ? null : travada.equipeId().toString());
		detalhes.put("servico", travada.servicoCodigo());
		detalhes.put("motivo", justificativa.motivoId().toString());
		auditoria.registrar(TipoEvento.ORDEM_DEVOLVIDA, "ordem-de-servico/" + id, detalhes);
		return visivel(id, "OS devolvida");
	}

	private OrdemServicoRepository.Travada daEquipe(UUID id) {
		var travada = ordens.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (travada.equipeId() == null || !equipes.equipesDe(usuario()).contains(travada.equipeId())) {
			throw new RecursoNaoEncontradoException();
		}
		return travada;
	}

	private OrdemServico encerrar(UUID id, OrdemServicoRepository.Travada travada, RegistroResultado registro,
			boolean pelaEquipe) {
		if (!travada.status().encerravel()) {
			throw new ConflitoException(switch (travada.status()) {
				case PLANEJADA, DEVOLVIDA -> "Programe a OS para uma equipe antes de encerrar.";
				case PARALISADA -> "Retome a OS paralisada antes de encerrar.";
				default -> "Esta OS já foi encerrada ou cancelada.";
			});
		}
		Justificativa justificativa = null;
		if (registro.resultado() == ResultadoOrdem.NAO_EXECUTADA) {
			justificativa = motivos.validar(registro.motivoId(), "NAO_EXECUCAO", travada.servicoId(),
					registro.observacao(), "Informe por que a OS não foi executada.");
		}
		var observacao = justificativa != null ? justificativa.observacao()
				: MotivosDaOrdem.observacao(registro.observacao());
		var quantidade = quantidade(registro, travada.quantidadePrevista());
		var conferencia = registro.resultado() == ResultadoOrdem.EXECUTADA && travada.conferidoPelaCarga()
				? Conferencia.PENDENTE : Conferencia.NAO_SE_APLICA;
		ordens.encerrar(id, registro.resultado(), justificativa == null ? null : justificativa.motivoId(), observacao,
				quantidade, usuario(), conferencia);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("campanha", travada.campanhaId() == null ? null : travada.campanhaId().toString());
		detalhes.put("servico", travada.servicoCodigo());
		detalhes.put("resultado", registro.resultado().name());
		detalhes.put("motivo", justificativa == null ? null : justificativa.motivoId().toString());
		detalhes.put("quantidade", quantidade.toPlainString());
		detalhes.put("registradoPor", pelaEquipe ? "EQUIPE" : "GESTOR");
		auditoria.registrar(TipoEvento.RESULTADO_DE_ORDEM_REGISTRADO, "ordem-de-servico/" + id, detalhes);
		if (conferencia == Conferencia.PENDENTE) {
			// A carga do mês pode já ter chegado com o imóvel ligado.
			ordens.conferir(relogio.getZone().getId());
		}
		return visivel(id, "Encerramento de ordem de serviço");
	}

	/**
	 * Quantidade executada: na executada, a informada (maior que zero) ou a prevista; na
	 * não executada, zero.
	 */
	static BigDecimal quantidade(RegistroResultado registro, BigDecimal prevista) {
		if (registro.resultado() == ResultadoOrdem.NAO_EXECUTADA) {
			return BigDecimal.ZERO;
		}
		var informada = registro.quantidadeExecutada();
		if (informada == null) {
			return prevista;
		}
		if (informada.signum() <= 0 || informada.scale() > 2
				|| informada.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
			throw new RegraVioladaException("Informe a quantidade executada (maior que zero, até duas casas).");
		}
		return informada;
	}

	private OrdemServico visivel(UUID id, String motivo) {
		var ordem = ordens.buscar(id, relogio.instant()).orElseThrow();
		return dadosPessoais.aplicar(List.of(ordem), motivo).getFirst();
	}

	/**
	 * RN-19: confere as OS executadas pela situação mensal (roda depois de cada carga, no
	 * contexto do sistema do tenant, em transação própria).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int conferir() {
		return ordens.conferir(relogio.getZone().getId());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CampanhaDivergente> comOrdensDivergentes() {
		return ordens.campanhasComDivergencia();
	}

	static UUID usuario() {
		var id = ContextoAcesso.exigir().usuarioId();
		if (id == null) {
			throw new IllegalStateException("Operação de usuário fora de uma requisição");
		}
		return id;
	}

}
