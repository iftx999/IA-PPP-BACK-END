package dev.aurora.campanhas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Serviço pela ação da campanha e o ciclo da OS (ADR 0036, 0044; doc 10, 10.6). */
class RegrasDasOrdensTest {

	@Test
	void acaoDefineOServicoDoCatalogo() {
		assertThat(AcaoCampanha.LIGACAO.servicoPara("FACTIVEL")).isEqualTo("LIGACAO_NOVA");
		assertThat(AcaoCampanha.LIGACAO.servicoPara("CORTADO")).isEqualTo("RELIGACAO");
		assertThat(AcaoCampanha.VISTORIA.servicoPara("CORTADO")).isEqualTo("VISTORIA");
		assertThat(AcaoCampanha.NOTIFICACAO.servicoPara("FACTIVEL")).isEqualTo("NOTIFICACAO");
	}

	@Test
	void abertasEComEquipe() {
		assertThat(StatusOrdem.ABERTAS).containsExactlyInAnyOrder(StatusOrdem.PLANEJADA, StatusOrdem.PROGRAMADA,
				StatusOrdem.EM_EXECUCAO, StatusOrdem.PARALISADA, StatusOrdem.DEVOLVIDA);
		assertThat(StatusOrdem.ENCERRADA.aberta()).isFalse();
		assertThat(StatusOrdem.CANCELADA.aberta()).isFalse();
		assertThat(StatusOrdem.DEVOLVIDA.comEquipe()).as("devolvida volta para a programação").isFalse();
		assertThat(StatusOrdem.PARALISADA.comEquipe()).isTrue();
	}

	@Test
	void transicoesDaTabela() {
		for (var s : StatusOrdem.values()) {
			var programavel = s == StatusOrdem.PLANEJADA || s == StatusOrdem.PROGRAMADA || s == StatusOrdem.DEVOLVIDA;
			assertThat(s.programavel()).as("programar %s", s).isEqualTo(programavel);
			assertThat(s.cancelavel()).as("cancelar %s", s).isEqualTo(programavel);
			assertThat(s.devolvivel()).as("devolver %s", s).isEqualTo(s.comEquipe());
			assertThat(s.encerravel()).as("encerrar %s", s)
				.isEqualTo(s == StatusOrdem.PROGRAMADA || s == StatusOrdem.EM_EXECUCAO);
		}
	}

}
