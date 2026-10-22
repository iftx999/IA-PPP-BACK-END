package dev.aurora.acesso.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ConfiguracaoWeb implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new InterceptadorPermissao()).addPathPatterns("/api/**");
	}

}
