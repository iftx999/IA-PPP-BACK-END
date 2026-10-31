package dev.aurora.adesao.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.AdesaoDaUnidade;
import dev.aurora.adesao.IndicadorDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.ProjecaoDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.adesao.model.Projecao;
import dev.aurora.adesao.repository.AdesaoRepository;
import dev.aurora.adesao.repository.MedicaoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MedicaoService implements Medicoes {

	private final MedicaoRepository medicoes;

	private final AdesaoRepository adesao;

	MedicaoService(MedicaoRepository medicoes, AdesaoRepository adesao) {
		this.medicoes = medicoes;
		this.adesao = adesao;
	}

	@Override
	@Transactional
	public int recalcular(YearMonth competencia) {
		var indicador = medicoes.garantirIndicador(AdesaoRepository.INDICADOR_ADESAO);
		return medicoes.recalcular(indicador, competencia);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdesaoDaUnidade> situacaoDas(Collection<UUID> unidades, YearMonth competencia) {
		var indicador = adesao.buscarIndicadorAdesao();
		if (indicador.isEmpty()) {
			return unidades.stream().map(u -> new AdesaoDaUnidade(u, null, Situacao.SEM_META)).toList();
		}
		var medidas = adesao.buscarMedicoes(unidades, indicador.get(), competencia);
		return unidades.stream().map(unidade -> {
			var medida = medidas.get(unidade);
			if (medida == null) {
				return new AdesaoDaUnidade(unidade, null, Situacao.SEM_META);
			}
			var meta = adesao.buscarMetaVigente(unidade, indicador.get(), competencia).orElse(null);
			return new AdesaoDaUnidade(unidade, medida.percentual(), Situacao.classificar(medida.percentual(), meta));
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<IndicadorDaUnidade> detalhar(Collection<UUID> unidades, YearMonth competencia) {
		var indicador = adesao.buscarIndicadorAdesao();
		if (indicador.isEmpty() || unidades.isEmpty()) {
			return List.of();
		}
		var atuais = adesao.buscarMedicoes(unidades, indicador.get(), competencia);
		var anteriores = adesao.buscarMedicoes(unidades, indicador.get(), competencia.minusMonths(1));
		var resultado = new ArrayList<IndicadorDaUnidade>();
		for (var unidade : unidades) {
			var atual = atuais.get(unidade);
			if (atual == null) {
				continue;
			}
			var anterior = anteriores.get(unidade);
			var meta = adesao.buscarMetaVigente(unidade, indicador.get(), competencia).orElse(null);
			var composicao = adesao.contarSemLigacao(unidade, competencia);
			var percentual = atual.percentual();
			BigDecimal falta = null;
			if (meta != null && percentual != null) {
				falta = meta.valorMeta().subtract(percentual).max(BigDecimal.ZERO);
			}
			resultado.add(new IndicadorDaUnidade(unidade, competencia, atual.ligados(), atual.comRede(), percentual,
					anterior == null ? null : anterior.percentual(), atual.variacaoPp(anterior),
					meta == null ? null : meta.valorMeta(), meta == null ? null : meta.valorAlerta(), falta,
					meta == null ? null : meta.vigenciaFim(), meta == null ? null : meta.origem(),
					meta != null && meta.herdada() ? meta.unidadeNome() : null, Situacao.classificar(percentual, meta),
					composicao.factiveis(), composicao.cortados()));
		}
		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProjecaoDaUnidade> projecoes() {
		var indicador = adesao.buscarIndicadorAdesao().orElse(null);
		if (indicador == null) {
			return List.of();
		}
		var competencias = adesao.listarCompetenciasDoEscopo(indicador);
		if (competencias.isEmpty()) {
			return List.of();
		}
		var atual = competencias.getFirst();
		var series = adesao.listarSeriesDoEscopo(indicador, atual.minusMonths(Projecao.JANELA_MESES), atual);
		var resultado = new ArrayList<ProjecaoDaUnidade>();
		series.forEach((unidade, serie) -> {
			var ultimo = serie.getLast();
			if (!ultimo.competencia().equals(atual)) {
				return;
			}
			var meta = adesao.buscarMetaVigente(unidade.id(), indicador, atual).orElse(null);
			if (meta == null || meta.vigenciaFim() == null) {
				return;
			}
			Projecao.calcular(serie, meta.valorMeta(), meta.vigenciaFim())
				.ifPresent(p -> resultado.add(new ProjecaoDaUnidade(unidade.id(), unidade.nome(), unidade.tipo(), atual,
						ultimo.adesao().percentual(), meta.valorMeta(), meta.valorAlerta(), !meta.herdada(),
						p.ritmoAtual(), p.ritmoNecessario(), p.faltam(), p.mesesRestantes(), p.projecaoNoPrazo(),
						p.prazo(), p.mesAtingimento(), p.atinge())));
		});
		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<YearMonth> competencias() {
		return adesao.buscarIndicadorAdesao().map(adesao::listarCompetenciasDoEscopo).orElse(List.of());
	}

}
