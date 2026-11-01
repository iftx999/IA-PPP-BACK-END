package dev.aurora.adesao.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.aurora.adesao.AdesaoDaUnidade;
import dev.aurora.adesao.BaseDaSimulacao;
import dev.aurora.adesao.BaseDaSimulacao.MesMedido;
import dev.aurora.adesao.BaseDaSimulacao.MetaDaSimulacao;
import dev.aurora.adesao.IndicadorDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.ProjecaoDaUnidade;
import dev.aurora.adesao.Situacao;
import dev.aurora.adesao.model.Projecao;
import dev.aurora.adesao.repository.AdesaoRepository;
import dev.aurora.adesao.repository.MedicaoRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MedicaoService implements Medicoes {

	/** Meses de série entregues ao simulador (como no painel, B3). */
	static final int MESES_NA_BASE = 12;

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

	@Override
	@Transactional(readOnly = true)
	public Optional<BaseDaSimulacao> baseDaSimulacao(@Nullable UUID unidadeId) {
		var indicador = adesao.buscarIndicadorAdesao().orElse(null);
		if (indicador == null) {
			return Optional.empty();
		}
		var unidade = (unidadeId == null ? adesao.buscarUnidadePadrao(indicador)
				: adesao.buscarUnidadeNoEscopo(unidadeId))
			.orElse(null);
		if (unidade == null) {
			return Optional.empty();
		}
		var competencias = adesao.listarCompetencias(unidade.id(), indicador);
		if (competencias.isEmpty()) {
			return Optional.empty();
		}
		var atual = competencias.getFirst();
		var serie = adesao.listarSerie(unidade.id(), indicador, atual.minusMonths(MESES_NA_BASE - 1), atual);
		if (serie.isEmpty() || serie.getLast().comRede() == 0) {
			return Optional.empty();
		}
		var meta = adesao.buscarMetaVigente(unidade.id(), indicador, atual).orElse(null);
		// O ritmo não depende da meta (RN-05); a meta de referência só entra no cálculo
		// de "faltam".
		var ritmo = Projecao.calcular(serie, meta == null ? BigDecimal.ZERO : meta.valorMeta(), null)
			.map(Projecao::ritmoAtual)
			.orElse(null);
		var metaDaSimulacao = meta == null ? null
				: new MetaDaSimulacao(meta.valorMeta(),
						meta.vigenciaFim() == null ? null : YearMonth.from(meta.vigenciaFim()), meta.unidadeId(),
						meta.unidadeNome(), meta.herdada(), meta.origem());
		return Optional.of(new BaseDaSimulacao(unidade.id(), unidade.nome(), unidade.tipo(), atual,
				serie.stream().map(p -> new MesMedido(p.competencia(), p.ligados(), p.comRede())).toList(), ritmo,
				metaDaSimulacao));
	}

}
