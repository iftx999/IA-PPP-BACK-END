package dev.aurora.acesso.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ConfiguracaoBanco {

	/**
	 * Envolve o DataSource da aplicação para que toda conexão carregue o contexto de
	 * acesso.
	 */
	@Bean
	static BeanPostProcessor contextoNoDataSource() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String nome) {
				if (bean instanceof DataSource dataSource && "dataSource".equals(nome)) {
					return new ContextoDataSource(dataSource);
				}
				return bean;
			}
		};
	}

}
