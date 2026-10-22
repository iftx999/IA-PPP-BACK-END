package dev.aurora.alertas.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tarefas agendadas ligadas por padrão; os testes as desligam e chamam o avaliador
 * diretamente.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "aurora.tarefas.habilitadas", havingValue = "true", matchIfMissing = true)
class ConfiguracaoAgendamento {

}
