package dev.aurora.comum;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesIa.class)
class ConfiguracaoPropriedadesIa {

}
