package dev.aurora.adesao.service;

import java.time.YearMonth;

import dev.aurora.adesao.Medicoes;
import dev.aurora.adesao.repository.AdesaoRepository;
import dev.aurora.adesao.repository.MedicaoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MedicaoService implements Medicoes {

	private final MedicaoRepository medicoes;

	MedicaoService(MedicaoRepository medicoes) {
		this.medicoes = medicoes;
	}

	@Override
	@Transactional
	public int recalcular(YearMonth competencia) {
		var indicador = medicoes.garantirIndicador(AdesaoRepository.INDICADOR_ADESAO);
		return medicoes.recalcular(indicador, competencia);
	}

}
