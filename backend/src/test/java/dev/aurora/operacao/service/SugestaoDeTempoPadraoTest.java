package dev.aurora.operacao.service;

import java.util.UUID;

import dev.aurora.operacao.model.Indicadores.TempoDoServico;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** J12: o tempo real medido sugere o ajuste do tempo padrão do serviço. */
class SugestaoDeTempoPadraoTest {

	@Test
	void sugereComAmostrasSuficientesEDiferencaAcimaDaTolerancia() {
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 12, 1.5, 20))).isEqualTo(30.0);
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 12, 0.6, 120))).isEqualTo(72.0);
	}

	@Test
	void naoSugereComPoucasAmostrasOuDentroDaTolerancia() {
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 9, 2.0, 20))).isNull();
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 50, 1.1, 20))).isNull();
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 50, null, 20))).isNull();
	}

	@Test
	void porUnidadeDeMedidaComDuasCasasELimites() {
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(true, 20, 1.37, 3))).isEqualTo(4.11);
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(true, 20, 0.001, 3))).isEqualTo(0.01);
		assertThat(IndicadoresDaOperacaoService.sugerir(tempo(false, 20, 30.0, 120))).isEqualTo(1440.0);
	}

	private static TempoDoServico tempo(boolean porUnidade, int amostras, @Nullable Double razao, double atual) {
		return new TempoDoServico(UUID.randomUUID(), "Serviço", porUnidade ? "M" : "UN", porUnidade, amostras, 0, 0,
				razao, atual, null);
	}

}
