package dev.aurora.campanhas.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.campanhas.model.Conflito;
import dev.aurora.campanhas.model.ExecucaoDaOrdem;
import dev.aurora.campanhas.model.PaginaConflitos;
import dev.aurora.campanhas.repository.AnexoRepository;
import dev.aurora.campanhas.repository.ConflitoRepository;
import dev.aurora.campanhas.repository.ExecucaoRepository;
import dev.aurora.campanhas.repository.OrdemServicoRepository;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.Servicos;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o campo registrou, visto pela programação (J9, J11; ADR 0045): histórico de
 * eventos, fotos e valores de uma OS e a caixa de conflitos com a decisão registrada.
 */
@Service
public class AcompanhamentoService {

	static final int TAMANHO_DA_PAGINA = 50;

	private final ConflitoRepository conflitos;

	private final AnexoRepository anexos;

	private final ExecucaoRepository execucao;

	private final OrdemServicoRepository ordens;

	private final Servicos servicos;

	private final Auditoria auditoria;

	private final Clock relogio;

	AcompanhamentoService(ConflitoRepository conflitos, AnexoRepository anexos, ExecucaoRepository execucao,
			OrdemServicoRepository ordens, Servicos servicos, Auditoria auditoria, Clock relogio) {
		this.conflitos = conflitos;
		this.anexos = anexos;
		this.execucao = execucao;
		this.ordens = ordens;
		this.servicos = servicos;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public ExecucaoDaOrdem execucao(UUID os) {
		ordens.buscar(os, relogio.instant()).orElseThrow(RecursoNaoEncontradoException::new);
		var versao = execucao.versaoDoServico(os).orElseThrow(RecursoNaoEncontradoException::new);
		return new ExecucaoDaOrdem(conflitos.eventos(os), anexos.daOrdem(os), conflitos.daOrdem(os),
				servicos.parametros(List.of(versao)).getOrDefault(versao, List.of()));
	}

	@Transactional(readOnly = true)
	public PaginaConflitos conflitos(boolean abertos, int pagina) {
		var p = Math.max(0, pagina);
		return new PaginaConflitos(conflitos.listar(abertos, p, TAMANHO_DA_PAGINA), conflitos.contar(abertos),
				conflitos.contar(true), p, TAMANHO_DA_PAGINA);
	}

	/**
	 * J11: a decisão do programador sobre o conflito (o que fez: reprogramou, cancelou,
	 * aceitou).
	 */
	@Transactional
	public Conflito decidir(UUID id, @Nullable String decisao) {
		var texto = decisao == null ? "" : decisao.strip();
		if (texto.length() < 3 || texto.length() > 500) {
			throw new RegraVioladaException("Descreva a decisão (de 3 a 500 caracteres).");
		}
		var conflito = conflitos.buscar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (!conflitos.decidir(id, texto, OrdemServicoService.usuario())) {
			throw new ConflitoException("Este conflito já foi decidido.");
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("conflito", id.toString());
		detalhes.put("tipo", conflito.tipo().name());
		auditoria.registrar(TipoEvento.CONFLITO_DE_CAMPO_DECIDIDO, "ordem-de-servico/" + conflito.osId(), detalhes);
		return conflitos.buscar(id).orElseThrow();
	}

}
