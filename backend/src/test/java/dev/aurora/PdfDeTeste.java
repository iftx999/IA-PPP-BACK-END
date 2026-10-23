package dev.aurora;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

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

	/**
	 * PDF "digitalizado": cada página é só uma imagem (300 DPI) do texto, sem camada de
	 * texto, como sai de um scanner.
	 */
	public static byte[] digitalizado(List<String> paginas) {
		try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
			for (var texto : paginas) {
				var pagina = new PDPage(PDRectangle.A4);
				documento.addPage(pagina);
				var imagem = imagemDoTexto(texto, 2480, 3508);
				try (var conteudo = new PDPageContentStream(documento, pagina)) {
					conteudo.drawImage(LosslessFactory.createFromImage(documento, imagem), 0, 0,
							PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
				}
			}
			documento.save(saida);
			return saida.toByteArray();
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
	}

	/**
	 * Página branca com o texto em preto (fonte serifada de 40 px, como impresso a 12 pt
	 * em 300 DPI).
	 */
	public static BufferedImage imagemDoTexto(String texto, int largura, int altura) {
		var imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_BYTE_GRAY);
		var g = imagem.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, largura, altura);
		g.setColor(Color.BLACK);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SERIF, Font.PLAIN, 40));
		int y = 250;
		for (var linha : texto.split("\n")) {
			g.drawString(linha, 200, y);
			y += 60;
		}
		g.dispose();
		return imagem;
	}

}
