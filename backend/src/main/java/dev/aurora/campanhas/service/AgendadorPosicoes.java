package dev.aurora.campanhas.service;

import java.time.Clock;
import java.time.Duration;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.comum.TarefasAgendadas;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RN-27 (P4): apaga as posições das equipes depois do prazo de guarda (90 dias), uma vez
 * por dia, em todos os tenants. Os indicadores agregados não dependem delas.
 */
@Component
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class AgendadorPosicoes {

	private static final Logger LOG = LoggerFactory.getLogger(AgendadorPosicoes.class);

	private static final String TAREFA = "expurgar-posicoes";

	private final ContextosDoSistema contextos;

	private final TarefasAgendadas tarefas;

	private final ExpurgoDePosicoes expurgo;

	AgendadorPosicoes(ContextosDoSistema contextos, TarefasAgendadas tarefas, ExpurgoDePosicoes expurgo) {
		this.contextos = contextos;
		this.tarefas = tarefas;
		this.expurgo = expurgo;
	}

	@Scheduled(initialDelayString = "${aurora.campo.expurgo.atraso-inicial:PT10M}",
			fixedDelayString = "${aurora.campo.expurgo.intervalo:PT24H}")
	void expurgar() {
		if (!tarefas.iniciar(TAREFA, Duration.ofHours(1))) {
			return;
		}
		int apagadas = 0;
		int falhas = 0;
		for (var contexto : contextos.deTodosOsTenants()) {
			try {
				apagadas += ContextoAcesso.executar(contexto, expurgo::expurgar);
			}
			catch (Exception erro) {
				falhas++;
				LOG.error("{} falhou no tenant {}", TAREFA, contexto.tenantId(), erro);
			}
		}
		if (apagadas > 0 || falhas > 0) {
			LOG.info("{}: {} posições apagadas, {} tenants com falha", TAREFA, apagadas, falhas);
		}
		tarefas.concluir(TAREFA, apagadas + " posições" + (falhas > 0 ? ", " + falhas + " tenants com falha" : ""));
	}

	/** O expurgo de um tenant, em transação própria. */
	@Component
	static class ExpurgoDePosicoes {

		private final dev.aurora.campanhas.repository.JornadaRepository jornadas;

		private final Clock relogio;

		private final Duration guarda;

		private final TransactionTemplate transacao;

		ExpurgoDePosicoes(dev.aurora.campanhas.repository.JornadaRepository jornadas, Clock relogio,
				@Value("${aurora.campo.posicoes.guarda:P90D}") Duration guarda, TransactionTemplate transacao) {
			this.jornadas = jornadas;
			this.relogio = relogio;
			this.guarda = guarda;
			this.transacao = transacao;
		}

		int expurgar() {
			var apagadas = transacao.execute(s -> jornadas.expurgarPosicoes(relogio.instant().minus(guarda)));
			return apagadas == null ? 0 : apagadas;
		}

	}

}
