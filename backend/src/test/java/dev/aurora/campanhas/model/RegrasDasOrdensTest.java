package dev.aurora.campanhas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tipo da OS pela ação, motivos por resultado e status em aberto (ADR 0036). */
class RegrasDasOrdensTest {

	@Test
	void acaoDefineOTipoDaOrdem() {
		assertThat(AcaoCampanha.LIGACAO.tipoPara("FACTIVEL")).isEqualTo(TipoOrdem.LIGACAO_NOVA);
		assertThat(AcaoCampanha.LIGACAO.tipoPara("CORTADO")).isEqualTo(TipoOrdem.RELIGACAO);
		assertThat(AcaoCampanha.VISTORIA.tipoPara("CORTADO")).isEqualTo(TipoOrdem.VISTORIA);
		assertThat(AcaoCampanha.NOTIFICACAO.tipoPara("FACTIVEL")).isEqualTo(TipoOrdem.NOTIFICACAO);
	}

	@Test
	void soLigacaoNovaEReligacaoSaoConferidas() {
		assertThat(TipoOrdem.LIGACAO_NOVA.conferidaPelaCarga()).isTrue();
		assertThat(TipoOrdem.RELIGACAO.conferidaPelaCarga()).isTrue();
		assertThat(TipoOrdem.VISTORIA.conferidaPelaCarga()).isFalse();
		assertThat(TipoOrdem.NOTIFICACAO.conferidaPelaCarga()).isFalse();
	}

	@Test
	void motivoCombinaComOResultado() {
		assertThat(MotivoResultado.SEM_INTERESSE.valePara(ResultadoOrdem.RECUSADA)).isTrue();
		assertThat(MotivoResultado.SEM_INTERESSE.valePara(ResultadoOrdem.INVIAVEL)).isFalse();
		assertThat(MotivoResultado.SEM_REDE_NA_RUA.valePara(ResultadoOrdem.INVIAVEL)).isTrue();
		assertThat(MotivoResultado.SEM_REDE_NA_RUA.valePara(ResultadoOrdem.RECUSADA)).isFalse();
		assertThat(MotivoResultado.OUTRO.valePara(ResultadoOrdem.RECUSADA)).isTrue();
		assertThat(MotivoResultado.OUTRO.valePara(ResultadoOrdem.INVIAVEL)).isTrue();
		assertThat(MotivoResultado.OUTRO.valePara(ResultadoOrdem.EXECUTADA)).isFalse();
		assertThat(MotivoResultado.CUSTO_DA_LIGACAO.valePara(ResultadoOrdem.REAGENDADA)).isFalse();
	}

	@Test
	void statusEmAbertoEEmCampo() {
		assertThat(StatusOrdem.PLANEJADA.aberta()).isTrue();
		assertThat(StatusOrdem.PLANEJADA.emCampo()).isFalse();
		assertThat(StatusOrdem.EM_CAMPO.emCampo()).isTrue();
		assertThat(StatusOrdem.REAGENDADA.emCampo()).isTrue();
		assertThat(StatusOrdem.REAGENDADA.aberta()).isTrue();
		assertThat(StatusOrdem.EXECUTADA.aberta()).isFalse();
		assertThat(StatusOrdem.CANCELADA.aberta()).isFalse();
		assertThat(ResultadoOrdem.RECUSADA.exigeMotivo()).isTrue();
		assertThat(ResultadoOrdem.REAGENDADA.exigeMotivo()).isFalse();
		assertThat(ResultadoOrdem.INVIAVEL.status()).isEqualTo(StatusOrdem.INVIAVEL);
	}

}
