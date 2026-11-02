package dev.aurora.campanhas.service;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.CargaConcluida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RN-19: depois de cada carga, confere as OS executadas pela nova situação dos imóveis.
 * Roda antes da reavaliação dos alertas, para o alerta de divergência já refletir a
 * carga.
 */
@Component
class ConferenciaAposCarga {

	/** Antes de {@code ReavaliacaoAposCarga} (alertas). */
	static final int ORDEM = 0;

	private static final Logger LOG = LoggerFactory.getLogger(ConferenciaAposCarga.class);

	private final OrdemServicoService ordens;

	private final ContextosDoSistema contextos;

	ConferenciaAposCarga(OrdemServicoService ordens, ContextosDoSistema contextos) {
		this.ordens = ordens;
		this.contextos = contextos;
	}

	@Order(ORDEM)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoConcluirCarga(CargaConcluida carga) {
		try {
			var contexto = contextos.doTenant(carga.tenantId()).orElse(null);
			if (contexto != null) {
				var mudaram = ContextoAcesso.executar(contexto, ordens::conferir);
				LOG.info("Conferência das OS após a carga {}: {} mudaram", carga.execucaoId(), mudaram);
			}
		}
		catch (Exception erro) {
			// A carga já foi gravada; a próxima carga ou resultado refaz a conferência.
			LOG.error("Falha ao conferir as OS após a carga {}", carga.execucaoId(), erro);
		}
	}

}
