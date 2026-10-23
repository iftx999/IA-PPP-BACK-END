package dev.aurora.contratos.integracao;

import java.time.Duration;
import java.util.List;

import dev.aurora.PdfDeTeste;
import dev.aurora.contratos.service.ReconhecedorDeTexto.ReconhecimentoFalhouException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OCR de verdade com o Tesseract (o CI instala tesseract-ocr-por; sem ele o teste é
 * ignorado).
 */
class ReconhecedorTesseractTest {

	private final ReconhecedorTesseract tesseract = new ReconhecedorTesseract("tesseract", Duration.ofSeconds(60));

	@Test
	void leTextoDaPaginaDigitalizadaEmPortugues() throws Exception {
		assumeTrue(tesseract.disponivel(), "Tesseract com português não instalado");
		var pdf = PdfDeTeste
			.digitalizado(List.of("CLÁUSULA SÉTIMA - DAS METAS\n7.2 A adesão mínima ao sistema de esgoto"
					+ "\nserá de 85% até 31/12/2030, com alerta em 80%."));
		var png = new LeitorPdfBox().renderizarPagina(pdf, 1, 300);

		var texto = tesseract.reconhecer(png);

		assertThat(texto).contains("7.2").contains("adesão mínima").contains("85%").contains("31/12/2030");
	}

	@Test
	void imagemInvalidaFalhaSemTravar() {
		assumeTrue(tesseract.disponivel(), "Tesseract com português não instalado");
		assertThatThrownBy(() -> tesseract.reconhecer("não é imagem".getBytes()))
			.isInstanceOf(ReconhecimentoFalhouException.class);
	}

	@Test
	void comandoInexistenteDeixaOOcrDesligado() {
		assertThat(new ReconhecedorTesseract("comando-que-nao-existe-aurora", Duration.ofSeconds(5)).disponivel())
			.isFalse();
	}

}
