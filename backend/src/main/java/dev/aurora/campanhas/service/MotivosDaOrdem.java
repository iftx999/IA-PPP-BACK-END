package dev.aurora.campanhas.service;

import java.util.UUID;

import dev.aurora.campanhas.model.Justificativa;
import dev.aurora.comum.RegraVioladaException;
import dev.aurora.servicos.MotivoDaOrdem;
import dev.aurora.servicos.Servicos;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * RN-22: motivo do catálogo de uma ação da OS (devolução, cancelamento, não execução): do
 * tipo certo, ativo e válido para o serviço; "exige observação" pede a observação (até
 * 500 caracteres).
 */
@Component
class MotivosDaOrdem {

	static final int OBSERVACAO_MAXIMA = 500;

	private final Servicos servicos;

	MotivosDaOrdem(Servicos servicos) {
		this.servicos = servicos;
	}

	Justificativa validar(@Nullable UUID motivoId, String tipo, UUID servico, @Nullable String observacao,
			String mensagem) {
		var texto = observacao(observacao);
		if (motivoId == null) {
			throw new RegraVioladaException(mensagem);
		}
		var motivo = servicos.motivo(motivoId)
			.filter(m -> m.valePara(tipo, servico))
			.orElseThrow(() -> new RegraVioladaException(mensagem));
		exigirObservacao(motivo, texto);
		return new Justificativa(motivo.id(), texto);
	}

	static void exigirObservacao(MotivoDaOrdem motivo, @Nullable String observacao) {
		if (motivo.exigeObservacao() && observacao == null) {
			throw new RegraVioladaException("Descreva o motivo na observação.");
		}
	}

	/** Observação sem espaços nas pontas; vazia vira nula. */
	static @Nullable String observacao(@Nullable String texto) {
		var limpo = texto == null || texto.isBlank() ? null : texto.strip();
		if (limpo != null && limpo.length() > OBSERVACAO_MAXIMA) {
			throw new RegraVioladaException("A observação tem no máximo " + OBSERVACAO_MAXIMA + " caracteres.");
		}
		return limpo;
	}

}
