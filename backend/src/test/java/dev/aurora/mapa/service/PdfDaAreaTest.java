package dev.aurora.mapa.service;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDaAreaTest {

	@Test
	void textoForaDaFontePadraoViraInterrogacao() {
		var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		assertThat(PdfDaArea.seguro(fonte, "Visita à rua São João · ••••001"))
			.isEqualTo("Visita à rua São João · ••••001");
		assertThat(PdfDaArea.seguro(fonte, "Cão 🐕\nlinha 2")).isEqualTo("Cão ? linha 2");
	}

}
