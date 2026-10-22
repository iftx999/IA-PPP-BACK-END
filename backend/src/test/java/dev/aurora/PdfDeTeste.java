package dev.aurora;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** PDFs gerados nos testes: uma string por página, linhas separadas por \n. */
public final class PdfDeTeste {

	private PdfDeTeste() {
	}

	public static byte[] comPaginas(List<String> paginas) {
		try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
			var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (var texto : paginas) {
				var pagina = new PDPage(PDRectangle.A4);
				documento.addPage(pagina);
				if (texto.isEmpty()) {
					continue;
				}
				try (var conteudo = new PDPageContentStream(documento, pagina)) {
					conteudo.beginText();
					conteudo.setFont(fonte, 11);
					conteudo.setLeading(14);
					conteudo.newLineAtOffset(50, 780);
					for (var linha : texto.split("\n")) {
						conteudo.showText(linha);
						conteudo.newLine();
					}
					conteudo.endText();
				}
			}
			documento.save(saida);
			return saida.toByteArray();
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
	}

}
