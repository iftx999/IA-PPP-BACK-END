package dev.aurora.adesao.service;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.model.LinhaUnidade;
import dev.aurora.adesao.model.Painel;
import dev.aurora.adesao.model.PontoSerie;
import dev.aurora.adesao.model.Projecao;
import dev.aurora.adesao.model.Situacao;
import dev.aurora.adesao.model.UnidadeResumo;
import dev.aurora.adesao.repository.AdesaoRepository;
import dev.aurora.comum.RecursoNaoEncontradoException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PainelService {

	/** Meses exibidos na série histórica (B3). */
	static final int MESES_NA_SERIE = 12;

	private final AdesaoRepository adesao;

	PainelService(AdesaoRepository adesao) {
		this.adesao = adesao;
	}

	/**
	 * Painel da unidade (padrão: a mais alta do escopo) na competência (padrão: a mais recente). Unidade fora do
	 * escopo ou competência sem medição respondem como não encontradas.
	 */
	@Transactional(readOnly = true)
	public Painel montar(Optional<UUID> unidadeId, Optional<YearMonth> competenciaPedida) {
		var indicador = adesao.buscarIndicadorAdesao().orElseThrow(RecursoNaoEncontradoException::new);
		UnidadeResumo unidade = unidadeId.map(id -> adesao.buscarUnidadeNoEscopo(id))
			.orElseGet(() -> adesao.buscarUnidadePadrao(indicador))
			.orElseThrow(RecursoNaoEncontradoException::new);

		var competencias = adesao.listarCompetencias(unidade.id(), indicador);
		if (competencias.isEmpty()) {
			throw new RecursoNaoEncontradoException();
		}
		var competencia = competenciaPedida.orElse(competencias.getFirst());
		if (!competencias.contains(competencia)) {
			throw new RecursoNaoEncontradoException();
		}

		var serie = adesao.listarSerie(unidade.id(), indicador, competencia.minusMonths(MESES_NA_SERIE - 1),
				competencia);
		var atual = serie.getLast().adesao();
		var anterior = serie.stream()
			.filter(p -> p.competencia().equals(competencia.minusMonths(1)))
			.map(PontoSerie::adesao)
			.findFirst()
			.orElse(null);

		var meta = adesao.buscarMetaVigente(unidade.id(), indicador, competencia).orElse(null);
		var projecao = meta == null ? null
				: Projecao.calcular(serie, meta.valorMeta(), meta.vigenciaFim()).orElse(null);

		var filhas = adesao.listarFilhas(unidade.id(), indicador, competencia).stream().map(filha -> {
			var metaFilha = adesao.buscarMetaVigente(filha.unidade().id(), indicador, competencia).orElse(null);
			return new LinhaUnidade(filha.unidade(), filha.atual(), filha.atual().variacaoPp(filha.anterior()),
					Situacao.classificar(filha.atual().percentual(), metaFilha), metaFilha);
		}).toList();

		return new Painel(unidade, competencia, competencias, atual,
				adesao.contarSemLigacao(unidade.id(), competencia), atual.variacaoPp(anterior),
				Situacao.classificar(atual.percentual(), meta), meta, projecao, serie, filhas);
	}

}
