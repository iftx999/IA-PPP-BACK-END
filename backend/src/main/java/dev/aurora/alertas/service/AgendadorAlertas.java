package dev.aurora.alertas.service;

import java.time.Duration;
import java.util.function.Supplier;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.comum.TarefasAgendadas;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agenda das tarefas de operação (F3): alertas a cada 15 minutos e trilha de auditoria
 * uma vez por dia, para cada tenant. Só uma instância da API executa cada tarefa por vez.
 */
@Component
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class AgendadorAlertas {

	private static final Logger LOG = LoggerFactory.getLogger(AgendadorAlertas.class);

	private final AvaliadorAlertas avaliador;

	private final ContextosDoSistema contextos;

	private final TarefasAgendadas tarefas;

	AgendadorAlertas(AvaliadorAlertas avaliador, ContextosDoSistema contextos, TarefasAgendadas tarefas) {
		this.avaliador = avaliador;
		this.contextos = contextos;
		this.tarefas = tarefas;
	}

	@Scheduled(initialDelayString = "${aurora.alertas.atraso-inicial:PT1M}",
			fixedDelayString = "${aurora.alertas.intervalo:PT15M}")
	void avaliarAlertas() {
		executar("avaliar-alertas", avaliador::avaliarIndicadores);
	}

	@Scheduled(initialDelayString = "${aurora.alertas.atraso-inicial:PT1M}",
			fixedDelayString = "${aurora.trilha.intervalo-verificacao:PT24H}")
	void verificarTrilha() {
		executar("verificar-trilha", avaliador::verificarTrilha);
	}

	private void executar(String tarefa, Supplier<AvaliadorAlertas.Resultado> acao) {
		if (!tarefas.iniciar(tarefa, Duration.ofMinutes(10))) {
			return;
		}
		int tenants = 0;
		int falhas = 0;
		for (var contexto : contextos.deTodosOsTenants()) {
			try {
				var resultado = ContextoAcesso.executar(contexto, acao::get);
				LOG.info("{} no tenant {}: {}", tarefa, contexto.tenantId(), resultado);
				tenants++;
			}
			catch (Exception erro) {
				falhas++;
				LOG.error("{} falhou no tenant {}", tarefa, contexto.tenantId(), erro);
			}
		}
		tarefas.concluir(tarefa, tenants + " tenants" + (falhas > 0 ? ", " + falhas + " com falha" : ""));
	}

}
