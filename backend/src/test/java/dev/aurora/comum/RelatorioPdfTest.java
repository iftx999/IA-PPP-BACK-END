package dev.aurora.comum;

import java.util.List;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioPdfTest {

	@Test
	void textoForaDaFontePadraoViraInterrogacao() {
		var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		assertThat(RelatorioPdf.seguro(fonte, "Visita à rua São João · ••••001"))
			.isEqualTo("Visita à rua São João · ••••001");
		assertThat(RelatorioPdf.seguro(fonte, "Cão 🐕\nlinha 2")).isEqualTo("Cão ? linha 2");
	}

	@Test
	void tabelaLongaVaiParaAsPaginasSeguintes() throws Exception {
		var linhas = java.util.stream.IntStream.range(0, 200).mapToObj(i -> List.of("Linha " + i, "Valor")).toList();
		var saida = new java.io.ByteArrayOutputStream();
		try (var pdf = new RelatorioPdf("Teste")) {
			pdf.titulo("Relatório").tabela(new float[] { 200, 200 }, List.of("A", "B"), linhas).salvar(saida);
		}
		assertThat(new String(saida.toByteArray(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
			.isEqualTo("%PDF-");
		try (var lido = org.apache.pdfbox.Loader.loadPDF(saida.toByteArray())) {
			assertThat(lido.getNumberOfPages()).isGreaterThan(3);
		}
	}

}
