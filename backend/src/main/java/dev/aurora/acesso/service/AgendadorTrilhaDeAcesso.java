package dev.aurora.acesso.service;

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
 * Copia os eventos de acesso do provedor de identidade para a trilha de cada tenant a
 * cada poucos minutos (ADR 0028). Só uma instância da API executa por vez; um tenant com
 * falha não impede os demais.
 */
@Component
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class AgendadorTrilhaDeAcesso {

	private static final Logger LOG = LoggerFactory.getLogger(AgendadorTrilhaDeAcesso.class);

	private static final String TAREFA = "importar-eventos-de-acesso";

	private final TrilhaDeAcessoService trilha;

	private final ContextosDoSistema contextos;

	private final TarefasAgendadas tarefas;

	AgendadorTrilhaDeAcesso(TrilhaDeAcessoService trilha, ContextosDoSistema contextos, TarefasAgendadas tarefas) {
		this.trilha = trilha;
		this.contextos = contextos;
		this.tarefas = tarefas;
	}

	@Scheduled(initialDelayString = "${aurora.trilha-de-acesso.atraso-inicial:PT1M}",
			fixedDelayString = "${aurora.trilha-de-acesso.intervalo:PT5M}")
	void importar() {
		if (!tarefas.iniciar(TAREFA, Duration.ofMinutes(10))) {
			return;
		}
		int eventos = 0;
		int falhas = 0;
		for (var contexto : contextos.deTodosOsTenants()) {
			try {
				eventos += ContextoAcesso.executar(contexto, trilha::importar);
			}
			catch (Exception erro) {
				falhas++;
				LOG.error("{} falhou no tenant {}", TAREFA, contexto.tenantId(), erro);
			}
		}
		if (eventos > 0 || falhas > 0) {
			LOG.info("{}: {} eventos novos, {} tenants com falha", TAREFA, eventos, falhas);
		}
		tarefas.concluir(TAREFA, eventos + " eventos" + (falhas > 0 ? ", " + falhas + " tenants com falha" : ""));
	}

}
