package dev.aurora.adesao.service;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.aurora.adesao.AdesaoDaUnidade;
import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.Situacao;
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

}
