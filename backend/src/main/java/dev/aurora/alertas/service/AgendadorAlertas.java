package dev.aurora.alertas.service;

import java.time.Duration;
import java.util.function.Supplier;

import dev.aurora.acesso.ContextosDoSistema;
import dev.aurora.auditoria.RetencaoDaTrilha;
import dev.aurora.comum.TarefasAgendadas;
import dev.aurora.contexto.ContextoAcesso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agenda das tarefas de operação (F3): alertas a cada 15 minutos e trilha de auditoria
 * (verificação e expurgo por retenção) uma vez por dia, para cada tenant; e-mail dos
 * alertas novos (ADR 0024). Só uma instância da API executa cada tarefa por vez.
 */
@Component
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class AgendadorAlertas {

	private static final Logger LOG = LoggerFactory.getLogger(AgendadorAlertas.class);

	private final AvaliadorAlertas avaliador;

	private final NotificadorAlertas notificador;

	private final ContextosDoSistema contextos;

	private final TarefasAgendadas tarefas;

	private final RetencaoDaTrilha retencao;

	AgendadorAlertas(AvaliadorAlertas avaliador, NotificadorAlertas notificador, ContextosDoSistema contextos,
			TarefasAgendadas tarefas, RetencaoDaTrilha retencao) {
		this.avaliador = avaliador;
		this.notificador = notificador;
		this.contextos = contextos;
		this.tarefas = tarefas;
		this.retencao = retencao;
	}

	@Scheduled(initialDelayString = "${aurora.alertas.atraso-inicial:PT1M}",
			fixedDelayString = "${aurora.alertas.intervalo:PT15M}")
	void avaliarAlertas() {
		executar("avaliar-alertas", avaliador::avaliarIndicadores);
	}

	/** ADR 0024: alertas novos por e-mail, logo depois de cada avaliação. */
	@Scheduled(initialDelayString = "${aurora.alertas.atraso-inicial-notificacao:PT2M}",
			fixedDelayString = "${aurora.alertas.intervalo:PT15M}")
	void notificarAlertas() {
		executar("notificar-alertas", notificador::notificar);
	}

	@Scheduled(initialDelayString = "${aurora.alertas.atraso-inicial:PT1M}",
			fixedDelayString = "${aurora.trilha.intervalo-verificacao:PT24H}")
	void verificarTrilha() {
		executar("verificar-trilha", avaliador::verificarTrilha);
	}

	/**
	 * ADR 0029: uma vez por dia, partições anuais com antecedência e expurgo do que
	 * passou do prazo de retenção de cada tenant.
	 */
	@Scheduled(initialDelayString = "${aurora.trilha.atraso-inicial-expurgo:PT3M}",
			fixedDelayString = "${aurora.trilha.intervalo-expurgo:PT24H}")
	void expurgarTrilha() {
		try {
			var criadas = retencao.prepararParticoes();
			if (criadas > 0) {
				LOG.info("partições da trilha criadas: {}", criadas);
			}
		}
		catch (RuntimeException erro) {
			LOG.error("não foi possível criar as partições da trilha", erro);
		}
		executar("expurgar-trilha", retencao::expurgarVencidos);
	}

	private void executar(String tarefa, Supplier<?> acao) {
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
