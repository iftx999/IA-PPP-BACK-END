package dev.aurora.operacao.service;

import java.time.Duration;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.comum.TarefasAgendadas;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda o detector de reotimização (ADR 0049) em todos os tenants, a cada 2 min por
 * padrão; com várias instâncias da API, só uma por vez (trava no banco).
 */
@Component
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class AgendadorReotimizacao {

	private static final Logger LOG = LoggerFactory.getLogger(AgendadorReotimizacao.class);

	private static final String TAREFA = "detectar-reotimizacao";

	private final ContextosDoSistema contextos;

	private final TarefasAgendadas tarefas;

	private final DetectorDeReotimizacao detector;

	AgendadorReotimizacao(ContextosDoSistema contextos, TarefasAgendadas tarefas, DetectorDeReotimizacao detector) {
		this.contextos = contextos;
		this.tarefas = tarefas;
		this.detector = detector;
	}

	@Scheduled(initialDelayString = "${aurora.operacao.deteccao.atraso-inicial:PT2M}",
			fixedDelayString = "${aurora.operacao.deteccao.intervalo:PT2M}")
	void detectar() {
		if (!tarefas.iniciar(TAREFA, Duration.ofMinutes(10))) {
			return;
		}
		int abertas = 0;
		int expiradas = 0;
		int falhas = 0;
		for (var contexto : contextos.deTodosOsTenants()) {
			try {
				var passada = ContextoAcesso.executar(contexto, detector::detectar);
				abertas += passada.abertas();
				expiradas += passada.expiradas();
			}
			catch (Exception erro) {
				falhas++;
				LOG.error("{} falhou no tenant {}", TAREFA, contexto.tenantId(), erro);
			}
		}
		if (abertas > 0 || falhas > 0) {
			LOG.info("{}: {} sugestões abertas, {} expiradas, {} tenants com falha", TAREFA, abertas, expiradas,
					falhas);
		}
		tarefas.concluir(TAREFA, abertas + " abertas, " + expiradas + " expiradas"
				+ (falhas > 0 ? ", " + falhas + " tenants com falha" : ""));
	}

}
