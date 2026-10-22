package dev.aurora.metas.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.auditoria.Auditoria;
import dev.aurora.auditoria.TipoEvento;
import dev.aurora.comum.ConflitoException;
import dev.aurora.comum.RecursoNaoEncontradoException;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.metas.model.LinhaMeta;
import dev.aurora.metas.MetaDoContrato;
import dev.aurora.metas.Metas;
import dev.aurora.metas.SituacaoDaUnidade;
import dev.aurora.metas.model.NovaMeta;
import dev.aurora.metas.model.ResumoMetas;
import dev.aurora.metas.repository.MetaRepository;
import dev.aurora.unidades.Unidades;
import org.jspecify.annotations.Nullable;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Metas de adesão por unidade e vigência (épico C, RN-06). */
@Service
public class MetaService implements Metas {

	/** SQLSTATE de violação de exclusion constraint (vigências sobrepostas). */
	private static final String VIOLACAO_DE_EXCLUSAO = "23P01";

	private static final String RECURSO = "meta/";

	private final MetaRepository metas;

	private final Unidades unidades;

	private final Auditoria auditoria;

	private final Clock relogio;

	MetaService(MetaRepository metas, Unidades unidades, Auditoria auditoria, Clock relogio) {
		this.metas = metas;
		this.unidades = unidades;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	public record Arvore(LocalDate referencia, ResumoMetas resumo, List<LinhaMeta> linhas) {
	}

	/** C1/C2: árvore do escopo com a meta vigente hoje (própria ou herdada). */
	@Transactional(readOnly = true)
	public Arvore arvore() {
		var indicador = indicador();
		var hoje = LocalDate.now(relogio);
		var linhas = metas.listarArvore(indicador, hoje);
		var seguem = linhas.stream().filter(l -> l.propria() == null && l.herdada() != null).count();
		var resumo = metas.resumir(indicador);
		return new Arvore(hoje, new ResumoMetas(resumo.cadastradas(), resumo.doContrato(), seguem), linhas);
	}

	/**
	 * C3: meta própria manual. A sobreposição de vigências é barrada pelo banco e vira
	 * 409.
	 */
	@Transactional
	public UUID criar(NovaMeta meta) {
		return criar(meta, null, null);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SituacaoDaUnidade> situacoes() {
		// Cliente sem indicador de adesão ainda (nenhuma carga): não há situação a
		// avaliar.
		if (metas.buscarIndicadorAdesao().isEmpty()) {
			return List.of();
		}
		return arvore().linhas().stream().map(l -> {
			var valor = l.propria() != null ? l.propria().valor() : l.herdada() != null ? l.herdada().valor() : null;
			var alerta = l.propria() != null ? l.propria().alerta() : l.herdada() != null ? l.herdada().alerta() : null;
			return new SituacaoDaUnidade(l.unidadeId(), l.unidadeNome(), l.tipo(), l.adesao(), l.competenciaAdesao(),
					valor, alerta, l.propria() != null, l.situacao());
		}).toList();
	}

	@Override
	@Transactional
	public UUID definirDoContrato(MetaDoContrato meta) {
		return criar(
				new NovaMeta(meta.unidadeId(), meta.valor(), meta.alerta(), meta.vigenciaInicio(), meta.vigenciaFim()),
				meta.itemContratualId(), meta.origem());
	}

	private UUID criar(NovaMeta meta, @Nullable UUID itemContratual, @Nullable String origem) {
		validar(meta);
		var unidade = unidades.buscarNoEscopo(meta.unidadeId()).orElseThrow(RecursoNaoEncontradoException::new);
		if (unidade.tipo().equals("ETE") || unidade.tipo().equals("EEE")) {
			throw new RegraVioladaException("Unidades operacionais (ETE/EEE) não têm meta de adesão.");
		}
		UUID id;
		try {
			id = metas.inserir(indicador(), meta, ContextoAcesso.exigir().usuarioId(), itemContratual, origem);
		}
		catch (DataIntegrityViolationException erro) {
			if (violouExclusao(erro)) {
				throw new ConflitoException("Já existe meta de " + unidade.nome() + " vigente nesse período.");
			}
			throw erro;
		}
		var detalhes = new LinkedHashMap<String, @Nullable Object>();
		detalhes.put("unidade", unidade.id().toString());
		detalhes.put("valor", meta.valor());
		detalhes.put("alerta", meta.alerta());
		detalhes.put("vigenciaInicio", meta.vigenciaInicio().toString());
		detalhes.put("vigenciaFim", meta.vigenciaFim() == null ? null : meta.vigenciaFim().toString());
		if (itemContratual != null) {
			detalhes.put("itemContratual", itemContratual.toString());
		}
		auditoria.registrar(TipoEvento.META_DEFINIDA, RECURSO + id, detalhes);
		return id;
	}

	/**
	 * C4: encerra a meta em uma data, sem apagar o histórico. Só antecipa o fim; metas
	 * vindas de contrato só mudam por aditivo aprovado.
	 */
	@Transactional
	public void encerrar(UUID id, LocalDate fim) {
		var meta = metas.buscarParaAlterar(id).orElseThrow(RecursoNaoEncontradoException::new);
		if (meta.doContrato()) {
			throw new ConflitoException("Meta de contrato só muda por aditivo aprovado.");
		}
		if (fim.isBefore(meta.vigenciaInicio())) {
			throw new RegraVioladaException("O fim não pode ser antes do início da vigência.");
		}
		if (meta.vigenciaFim() != null && fim.isAfter(meta.vigenciaFim())) {
			throw new RegraVioladaException("Encerrar só antecipa o fim da vigência.");
		}
		metas.encerrar(id, fim);
		auditoria.registrar(TipoEvento.META_ENCERRADA, RECURSO + id,
				Map.of("fimAnterior", String.valueOf(meta.vigenciaFim()), "fim", fim.toString()));
	}

	private static void validar(NovaMeta meta) {
		if (meta.alerta().compareTo(meta.valor()) > 0) {
			throw new RegraVioladaException("O limite de alerta não pode ser maior que a meta.");
		}
		if (meta.vigenciaFim() != null && meta.vigenciaFim().isBefore(meta.vigenciaInicio())) {
			throw new RegraVioladaException("O fim da vigência não pode ser antes do início.");
		}
	}

	private UUID indicador() {
		return metas.buscarIndicadorAdesao().orElseThrow(RecursoNaoEncontradoException::new);
	}

	private static boolean violouExclusao(Throwable erro) {
		for (var causa = erro; causa != null; causa = causa.getCause()) {
			if (causa instanceof java.sql.SQLException sql && VIOLACAO_DE_EXCLUSAO.equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

}
