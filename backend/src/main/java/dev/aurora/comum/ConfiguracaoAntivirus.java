package dev.aurora.comum;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Antivírus dos arquivos enviados (ADR 0033). Host vazio desliga; com
 * {@code aurora.antivirus.obrigatorio} (produção), arquivo não verificado é recusado.
 */
@Configuration
class ConfiguracaoAntivirus {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoAntivirus.class);

	@Bean
	Antivirus antivirus(@Value("${aurora.antivirus.host:}") String host,
			@Value("${aurora.antivirus.porta:3310}") int porta,
			@Value("${aurora.antivirus.tempo-maximo:PT60S}") Duration tempoMaximo) {
		if (host.isBlank()) {
			LOG.warn("Antivírus desligado: configure AURORA_ANTIVIRUS_HOST para verificar os arquivos enviados.");
			return conteudo -> new Antivirus.NaoVerificado("Antivírus desligado.");
		}
		LOG.info("Antivírus ClamAV em {}:{}", host, porta);
		return new ClamAvAntivirus(host, porta, tempoMaximo);
	}

}
