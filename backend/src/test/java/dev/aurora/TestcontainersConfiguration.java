package dev.aurora;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	DynamicPropertyRegistrar bancoDeTeste() {
		return registro -> {
			registro.add("spring.datasource.url", BancoDeTeste::url);
			registro.add("spring.datasource.password", () -> BancoDeTeste.SENHA_APP);
			registro.add("spring.flyway.password", () -> BancoDeTeste.SENHA_OWNER);
			// Cada contexto em cache guarda um pool: poucas conexões ociosas para não
			// esgotar o max_connections do banco de teste.
			registro.add("spring.datasource.hikari.minimum-idle", () -> "1");
			registro.add("spring.datasource.hikari.idle-timeout", () -> "10000");
			// Tarefas agendadas desligadas: os testes chamam o avaliador de alertas
			// diretamente.
			registro.add("aurora.tarefas.habilitadas", () -> "false");
			// Roteirização sem Valhalla (deslocamento em linha reta, determinístico) e
			// com otimização curta.
			registro.add("aurora.roteirizacao.valhalla.url", () -> "");
			registro.add("aurora.roteirizacao.tempo-previa", () -> "3s");
			registro.add("aurora.roteirizacao.tempo-reotimizacao", () -> "2s");
			registro.add("aurora.roteirizacao.sem-melhora", () -> "1s");
		};
	}

}
