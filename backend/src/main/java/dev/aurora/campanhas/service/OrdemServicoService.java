package dev.aurora.campanhas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.CampanhaDivergente;
import dev.aurora.campanhas.Campanhas;
import dev.aurora.campanhas.model.Conferencia;
import dev.aurora.campanhas.model.MotivoResultado;
import dev.aurora.campanhas.model.OrdemServico;
import dev.aurora.campanhas.model.RegistroResultado;
import dev.aurora.campanhas.model.ResultadoOrdem;
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
 * Ordens de serviço no campo (I3) e a conferência pela carga (I4, RN-19). A equipe só vê
 * e registra as OS das equipes de que faz parte; o gestor registra por qualquer equipe da
 * sua área (ex.: empreiteira sem acesso à Aurora).
 */
@Service
public class OrdemServicoService implements Campanhas {

	/** Resultados recentes que continuam na lista da equipe. */
	static final int DIAS_DE_HISTORICO = 30;

	/** Reagendamento máximo a partir de hoje. */
	static final int REAGENDAMENTO_MAXIMO_EM_DIAS = 180;

	static final int OBSERVACAO_MAXIMA = 500;

	private final OrdemServicoRepository ordens;

	private final EquipeRepository equipes;

	private final DadosPessoaisDasOrdens dadosPessoais;

	private final Auditoria auditoria;

	private final Clock relogio;

	OrdemServicoService(OrdemServicoRepository ordens, EquipeRepository equipes, DadosPessoaisDasOrdens dadosPessoais,
			Auditoria auditoria, Clock relogio) {
		this.ordens = ordens;
		this.equipes = equipes;
		this.dadosPessoais = dadosPessoais;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** OS em campo das equipes da pessoa e os resultados dos últimos 30 dias. */
	@Transactional
	public List<OrdemServico> minhas() {
		var hoje = LocalDate.now(relogio);
		var lista = ordens.dasEquipes(equipes.equipesDe(usuario()), hoje, hoje.minusDays(DIAS_DE_HISTORICO));
		return dadosPessoais.aplicar(lista, "Ordens de serviço da equipe");
	}

	/** Resultado informado por alguém da equipe da OS. */
	@Transactional
	public OrdemServico registrarPelaEquipe(UUID id, RegistroResultado registro) {
		var travada = ordens.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (travada.equipeId() == null || !equipes.equipesDe(usuario()).contains(travada.equipeId())) {
			throw new RecursoNaoEncontradoException();
		}
		return registrar(id, travada, registro, true);
	}

	/** Resultado informado pelo gestor da campanha (ex.: planilha da empreiteira). */
	@Transactional
	public OrdemServico registrarPeloGestor(UUID id, RegistroResultado registro) {
		var travada = ordens.travar(id).orElseThrow(RecursoNaoEncontradoException::new);
		return registrar(id, travada, registro, false);
	}

	private OrdemServico registrar(UUID id, OrdemServicoRepository.Travada travada, RegistroResultado registro,
			boolean pelaEquipe) {
		if (!travada.status().emCampo()) {
			throw new ConflitoException(travada.status().aberta() ? "Envie a OS a uma equipe antes do resultado."
					: "Esta OS já tem resultado.");
		}
		var hoje = LocalDate.now(relogio);
		var normalizado = validar(registro, hoje);
		var conferencia = normalizado.resultado() == ResultadoOrdem.EXECUTADA && travada.tipo().conferidaPelaCarga()
				? Conferencia.PENDENTE : Conferencia.NAO_SE_APLICA;
		ordens.registrarResultado(id, normalizado, usuario(), conferencia);
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("campanha", travada.campanhaId().toString());
		detalhes.put("tipo", travada.tipo().name());
		detalhes.put("resultado", normalizado.resultado().name());
		detalhes.put("motivo", normalizado.motivo() == null ? null : normalizado.motivo().name());
		detalhes.put("reagendadaPara",
				normalizado.reagendadaPara() == null ? null : normalizado.reagendadaPara().toString());
		detalhes.put("registradoPor", pelaEquipe ? "EQUIPE" : "GESTOR");
		auditoria.registrar(TipoEvento.RESULTADO_DE_ORDEM_REGISTRADO, "ordem-de-servico/" + id, detalhes);
		if (conferencia == Conferencia.PENDENTE) {
			// A carga do mês pode já ter chegado com o imóvel ligado.
			ordens.conferir(relogio.getZone().getId());
		}
		var ordem = ordens.buscar(id, hoje).orElseThrow();
		return dadosPessoais.aplicar(List.of(ordem), "Resultado de ordem de serviço").getFirst();
	}

	static RegistroResultado validar(RegistroResultado r, LocalDate hoje) {
		var observacao = r.observacao() == null || r.observacao().isBlank() ? null : r.observacao().strip();
		if (observacao != null && observacao.length() > OBSERVACAO_MAXIMA) {
			throw new RegraVioladaException("A observação tem no máximo " + OBSERVACAO_MAXIMA + " caracteres.");
		}
		MotivoResultado motivo = null;
		if (r.resultado().exigeMotivo()) {
			if (r.motivo() == null || !r.motivo().valePara(r.resultado())) {
				throw new RegraVioladaException(r.resultado() == ResultadoOrdem.RECUSADA ? "Informe o motivo da recusa."
						: "Informe por que a OS é inviável.");
			}
			if (r.motivo() == MotivoResultado.OUTRO && observacao == null) {
				throw new RegraVioladaException("Descreva o motivo na observação.");
			}
			motivo = r.motivo();
		}
		LocalDate reagendada = null;
		if (r.resultado() == ResultadoOrdem.REAGENDADA) {
			if (r.reagendadaPara() == null || r.reagendadaPara().isBefore(hoje)
					|| r.reagendadaPara().isAfter(hoje.plusDays(REAGENDAMENTO_MAXIMO_EM_DIAS))) {
				throw new RegraVioladaException("Informe a nova data (de hoje até 180 dias).");
			}
			reagendada = r.reagendadaPara();
		}
		return new RegistroResultado(r.resultado(), motivo, observacao, reagendada);
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

	private static UUID usuario() {
		var id = ContextoAcesso.exigir().usuarioId();
		if (id == null) {
			throw new IllegalStateException("Operação de usuário fora de uma requisição");
		}
		return id;
	}

}
