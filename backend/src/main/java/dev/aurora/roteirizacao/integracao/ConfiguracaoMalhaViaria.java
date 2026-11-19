package dev.aurora.roteirizacao.integracao;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

import dev.aurora.comum.Disjuntores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoMalhaViaria.Propriedades.class)
class ConfiguracaoMalhaViaria {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoMalhaViaria.class);

	/**
	 * @param url endereço do Valhalla na rede interna (ex.: http://valhalla:8002); vazio
	 * = só estimativa em linha reta
	 * @param tempoLimite leitura de cada pedido (um bloco da matriz ou uma rota)
	 * @param paresNoCache pares origem-destino guardados (~90 bytes cada)
	 * @param concorrencia pedidos de matriz em paralelo
	 */
	@ConfigurationProperties("aurora.roteirizacao.valhalla")
	record Propriedades(String url, Duration tempoLimite, int paresNoCache, int concorrencia) {
	}

	@Bean(destroyMethod = "close")
	MalhaViariaValhalla malhaViaria(Propriedades propriedades, Disjuntores disjuntores) {
		ValhallaCliente cliente = null;
		if (propriedades.url() != null && !propriedades.url().isBlank()) {
			var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
			var fabrica = new JdkClientHttpRequestFactory(http);
			fabrica.setReadTimeout(propriedades.tempoLimite());
			cliente = new ValhallaCliente(
					RestClient.builder().requestFactory(fabrica).baseUrl(propriedades.url()).build());
			LOG.info("Rotas pela malha viária: Valhalla em {}", propriedades.url());
		}
		else {
			LOG.warn("Roteirização sem malha viária: configure AURORA_VALHALLA_URL; deslocamentos em linha reta.");
		}
		// Pedido recusado (400: ponto ou perfil inválido) não indica serviço fora do ar.
		var disjuntor = disjuntores.de(Disjuntores.ROTAS, erro -> erro instanceof HttpClientErrorException);
		return new MalhaViariaValhalla(cliente, disjuntor, propriedades.paresNoCache(), propriedades.concorrencia(),
				Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("valhalla-", 0).factory()));
	}

}
