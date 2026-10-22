package dev.aurora.alertas.service;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.contexto.ContextoAcesso;
import dev.aurora.integracoes.CargaConcluida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Depois de uma carga, reavalia os alertas do tenant na hora (a fonte deixa de estar
 * atrasada e a adesão pode ter mudado), sem esperar a próxima rodada de 15 minutos.
 */
@Component
class ReavaliacaoAposCarga {

	private static final Logger LOG = LoggerFactory.getLogger(ReavaliacaoAposCarga.class);

	private final AvaliadorAlertas avaliador;

	private final ContextosDoSistema contextos;

	ReavaliacaoAposCarga(AvaliadorAlertas avaliador, ContextosDoSistema contextos) {
		this.avaliador = avaliador;
		this.contextos = contextos;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoConcluirCarga(CargaConcluida carga) {
		try {
			var contexto = contextos.doTenant(carga.tenantId()).orElse(null);
			if (contexto != null) {
				var resultado = ContextoAcesso.executar(contexto, avaliador::avaliarIndicadores);
				LOG.info("Alertas reavaliados após a carga {}: {}", carga.execucaoId(), resultado);
			}
		}
		catch (Exception erro) {
			// A carga já foi gravada; a próxima rodada agendada corrige os alertas.
			LOG.error("Falha ao reavaliar alertas após a carga {}", carga.execucaoId(), erro);
		}
	}

}
