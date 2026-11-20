package dev.aurora.roteirizacao.service;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.campanhas.JornadaIniciada;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * D6 (ADR 0048): ao abrir a jornada no app, pede a rota do dia da equipe a partir da
 * posição do agente. Falhar aqui não atrapalha a jornada: o app segue com a ordem da
 * programação.
 */
@Component
@ConditionalOnProperty(name = "aurora.roteirizacao.na-jornada", havingValue = "true", matchIfMissing = true)
class RotaAoIniciarJornada {

	private static final Logger LOG = LoggerFactory.getLogger(RotaAoIniciarJornada.class);

	private final RoteirizacaoService roteirizacoes;

	private final ContextosDoSistema contextos;

	RotaAoIniciarJornada(RoteirizacaoService roteirizacoes, ContextosDoSistema contextos) {
		this.roteirizacoes = roteirizacoes;
		this.contextos = contextos;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoIniciar(JornadaIniciada jornada) {
		try {
			var contexto = contextos.doTenant(jornada.tenantId()).orElse(null);
			if (contexto != null) {
				ContextoAcesso.executar(contexto, () -> roteirizacoes.calcularNaJornada(jornada));
			}
		}
		catch (Exception erro) {
			LOG.error("Falha ao pedir a rota da jornada {}", jornada.jornadaId(), erro);
		}
	}

}
