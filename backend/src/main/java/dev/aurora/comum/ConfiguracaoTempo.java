package dev.aurora.comum;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio da aplicação no fuso do negócio (injetável, para testes determinísticos). */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoTempo {

	static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Bean
	Clock relogio() {
		return Clock.system(FUSO);
	}

}
