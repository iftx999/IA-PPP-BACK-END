package dev.aurora.contratos.integracao;

import java.time.Duration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.aurora.comum.PropriedadesIa;
import dev.aurora.contratos.service.ExtratorContrato;
import dev.aurora.contratos.service.ReconhecedorDeTexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Extrator pelo provedor de {@code aurora.ia} (ADR 0014, 0019). */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoIa {

	private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoIa.class);

	private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(5);

	/**
	 * OCR local (ADR 0021): {@code aurora.ocr.comando} aponta o binário do Tesseract;
	 * vazio desliga.
	 */
	@Bean
	ReconhecedorDeTexto reconhecedorDeTexto(@Value("${aurora.ocr.comando:tesseract}") String comando,
			@Value("${aurora.ocr.segundos-por-pagina:90}") int segundos) {
		if (comando.isBlank()) {
			LOG.warn("OCR desligado por configuração (aurora.ocr.comando vazio).");
			return new OcrDesligado();
		}
		var tesseract = new ReconhecedorTesseract(comando, Duration.ofSeconds(segundos));
		return tesseract.disponivel() ? tesseract : new OcrDesligado();
	}

	@Bean
	ExtratorContrato extratorContrato(PropriedadesIa ia) {
		if (!ia.ligada()) {
			LOG.warn("Extração de contratos por IA desligada: configure o provedor {} para ligar.", ia.usaOpenAi()
					? "OpenAI (AURORA_IA_OPENAI_CHAVE e AURORA_IA_OPENAI_MODELO)" : "Claude (AURORA_IA_CHAVE)");
			return new ExtratorDesligado();
		}
		if (ia.usaOpenAi()) {
			var openAi = ia.openAi();
			var cliente = OpenAIOkHttpClient.builder().apiKey(openAi.chave()).timeout(TEMPO_MAXIMO).maxRetries(3);
			if (PropriedadesIa.preenchido(openAi.urlBase())) {
				cliente.baseUrl(openAi.urlBase());
			}
			return new ExtratorOpenAi(cliente.build(), openAi);
		}
		var cliente = AnthropicOkHttpClient.builder().apiKey(ia.chave()).timeout(TEMPO_MAXIMO).maxRetries(3);
		if (PropriedadesIa.preenchido(ia.urlBase())) {
			cliente.baseUrl(ia.urlBase());
		}
		return new ExtratorClaude(cliente.build(), ia.modelo());
	}

}
