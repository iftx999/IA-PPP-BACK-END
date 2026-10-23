package dev.aurora.contratos.integracao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.aurora.contratos.service.ReconhecedorDeTexto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OCR com o Tesseract (Apache-2.0) em português. O binário é chamado com argumentos
 * fixos, sem shell; a imagem entra pela entrada padrão e o texto sai pela saída padrão.
 * Cada página tem tempo máximo.
 */
class ReconhecedorTesseract implements ReconhecedorDeTexto {

	private static final Logger LOG = LoggerFactory.getLogger(ReconhecedorTesseract.class);

	private final String comando;

	private final Duration tempoPorPagina;

	private final boolean disponivel;

	ReconhecedorTesseract(String comando, Duration tempoPorPagina) {
		this.comando = comando;
		this.tempoPorPagina = tempoPorPagina;
		this.disponivel = verificar();
	}

	@Override
	public boolean disponivel() {
		return disponivel;
	}

	@Override
	public String reconhecer(byte[] png) throws ReconhecimentoFalhouException {
		Process processo = null;
		try {
			// --psm 1: segmentação automática com detecção de orientação (contratos
			// digitalizados tortos).
			processo = new ProcessBuilder(comando, "stdin", "stdout", "-l", "por", "--psm", "1")
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			var saida = processo.getInputStream();
			var leitura = CompletableFuture.supplyAsync(() -> {
				try {
					return new String(saida.readAllBytes(), StandardCharsets.UTF_8);
				}
				catch (IOException erro) {
					throw new IllegalStateException(erro);
				}
			});
			try (var entrada = processo.getOutputStream()) {
				entrada.write(png);
			}
			if (!processo.waitFor(tempoPorPagina.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new ReconhecimentoFalhouException("O reconhecimento de texto demorou demais nesta página.");
			}
			if (processo.exitValue() != 0) {
				throw new ReconhecimentoFalhouException("O reconhecimento de texto falhou nesta página.");
			}
			return leitura.get(5, TimeUnit.SECONDS).strip();
		}
		catch (IOException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException erro) {
			throw new ReconhecimentoFalhouException("O reconhecimento de texto falhou nesta página.", erro);
		}
		catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
			throw new ReconhecimentoFalhouException("O reconhecimento de texto foi interrompido.", erro);
		}
		finally {
			if (processo != null && processo.isAlive()) {
				processo.destroyForcibly();
			}
		}
	}

	/** O binário existe e tem o idioma português instalado. */
	private boolean verificar() {
		try {
			var processo = new ProcessBuilder(List.of(comando, "--list-langs")).redirectErrorStream(true).start();
			var saida = new ByteArrayOutputStream();
			processo.getInputStream().transferTo(saida);
			if (!processo.waitFor(10, TimeUnit.SECONDS) || processo.exitValue() != 0) {
				return false;
			}
			var temPortugues = saida.toString(StandardCharsets.UTF_8).lines().anyMatch(l -> l.strip().equals("por"));
			if (!temPortugues) {
				LOG.warn("Tesseract encontrado sem o idioma português (instale tesseract-ocr-por); OCR desligado.");
			}
			return temPortugues;
		}
		catch (IOException erro) {
			LOG.warn("OCR desligado: comando '{}' não encontrado. Instale tesseract-ocr e tesseract-ocr-por.", comando);
			return false;
		}
		catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
