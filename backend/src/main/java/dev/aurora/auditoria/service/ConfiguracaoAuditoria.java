package dev.aurora.auditoria.service;

import java.nio.charset.StandardCharsets;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ConfiguracaoAuditoria {

	/**
	 * A chave fica fora do banco: quem só tem acesso ao banco não consegue refazer a
	 * cadeia.
	 */
	@Bean
	AssinaturaEvento assinaturaEvento(@Value("${aurora.auditoria.chave}") String chave, JsonMapper json) {
		return new AssinaturaEvento(chave.getBytes(StandardCharsets.UTF_8), json);
	}

}
