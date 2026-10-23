package dev.aurora.comum;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Relatório em PDF das exportações (RN-12): A4 deitado, rodapé "registrada na auditoria"
 * com número da página, títulos, linhas de texto, imagem e tabelas que continuam na
 * página seguinte. Fontes padrão do PDF: o que elas não codificam vira "?".
 */
public final class RelatorioPdf implements AutoCloseable {

	private static final PDRectangle PAGINA = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

	private static final float MARGEM = 36;

	private static final float LINHA = 13;

	private final PDDocument documento = new PDDocument();

	private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private PDPageContentStream conteudo;

	private float y;

	private int pagina;

	public RelatorioPdf(String titulo) {
		var info = new PDDocumentInformation();
		info.setTitle(titulo);
		info.setProducer("Aurora");
		documento.setDocumentInformation(info);
		executar(this::novaPagina);
	}

	/** Largura útil da página, para dividir as colunas das tabelas. */
	public static float larguraUtil() {
		return PAGINA.getWidth() - 2 * MARGEM;
	}

	public RelatorioPdf titulo(String texto) {
		executar(() -> {
			texto(negrito, 16, MARGEM, texto);
			y -= 18;
		});
		return this;
	}

	public RelatorioPdf subtitulo(String texto) {
		executar(() -> {
			garantirEspaco(LINHA * 4);
			y -= 10;
			texto(negrito, 12, MARGEM, texto);
			y -= 16;
		});
		return this;
	}

	/** Linha de texto (corta no fim da página). */
	public RelatorioPdf linha(String texto, boolean destaque, float tamanho) {
		executar(() -> {
			garantirEspaco(LINHA);
			texto(destaque ? negrito : normal, tamanho, MARGEM, cortar(destaque ? negrito : normal, tamanho,
					seguro(destaque ? negrito : normal, texto), larguraUtil()));
			y -= tamanho + 5;
		});
		return this;
	}

	public RelatorioPdf espaco(float pontos) {
		y -= pontos;
		return this;
	}

	/** Imagem PNG na largura útil, até a altura indicada. */
	public RelatorioPdf imagem(byte[] png, float alturaMaxima) {
		executar(() -> {
			var imagem = PDImageXObject.createFromByteArray(documento, png, "imagem.png");
			float altura = Math.min(alturaMaxima, larguraUtil() * imagem.getHeight() / imagem.getWidth());
			garantirEspaco(altura + 14);
			float largura = altura * imagem.getWidth() / imagem.getHeight();
			y -= altura;
			conteudo.drawImage(imagem, MARGEM, y, largura, altura);
			y -= 14;
		});
		return this;
	}

	/**
	 * Tabela com cabeçalho; ao virar a página, o cabeçalho é repetido.
	 * @param larguras largura de cada coluna, em pontos
	 */
	public RelatorioPdf tabela(float[] larguras, List<String> cabecalho, List<List<String>> linhas) {
		executar(() -> {
			garantirEspaco(LINHA * 2);
			cabecalho(larguras, cabecalho);
			for (var linha : linhas) {
				if (garantirEspaco(LINHA)) {
					cabecalho(larguras, cabecalho);
				}
				escreverLinha(normal, larguras, linha);
			}
		});
		return this;
	}

	public void salvar(OutputStream saida) {
		executar(() -> {
			conteudo.close();
			conteudo = null;
			documento.save(saida);
		});
	}

	@Override
	public void close() {
		executar(() -> {
			if (conteudo != null) {
				conteudo.close();
			}
			documento.close();
		});
	}

	private void cabecalho(float[] larguras, List<String> celulas) throws IOException {
		escreverLinha(negrito, larguras, celulas);
		conteudo.setLineWidth(0.5f);
		conteudo.moveTo(MARGEM, y + LINHA - 3);
		conteudo.lineTo(PAGINA.getWidth() - MARGEM, y + LINHA - 3);
		conteudo.stroke();
	}

	private void escreverLinha(PDType1Font fonte, float[] larguras, List<String> celulas) throws IOException {
		float x = MARGEM;
		for (int i = 0; i < celulas.size() && i < larguras.length; i++) {
			var celula = celulas.get(i) == null ? "" : celulas.get(i);
			texto(fonte, 8, x, cortar(fonte, 8, seguro(fonte, celula), larguras[i] - 6));
			x += larguras[i];
		}
		y -= LINHA;
	}

	/**
	 * @return se abriu página nova
	 */
	private boolean garantirEspaco(float altura) throws IOException {
		if (y - altura < MARGEM + 16) {
			conteudo.close();
			novaPagina();
			return true;
		}
		return false;
	}

	private void novaPagina() throws IOException {
		var nova = new PDPage(PAGINA);
		documento.addPage(nova);
		conteudo = new PDPageContentStream(documento, nova);
		pagina++;
		y = PAGINA.getHeight() - MARGEM;
		texto(normal, 7, MARGEM, "Aurora · Exportação registrada na trilha de auditoria");
		var numero = "Página " + pagina;
		texto(normal, 7, PAGINA.getWidth() - MARGEM - normal.getStringWidth(numero) / 1000 * 7, numero);
		conteudo.setLineWidth(0.3f);
		conteudo.moveTo(MARGEM, y - 4);
		conteudo.lineTo(PAGINA.getWidth() - MARGEM, y - 4);
		conteudo.stroke();
		y -= 22;
	}

	private void texto(PDType1Font fonte, float tamanho, float x, String texto) throws IOException {
		conteudo.beginText();
		conteudo.setFont(fonte, tamanho);
		conteudo.newLineAtOffset(x, y);
		conteudo.showText(seguro(fonte, texto));
		conteudo.endText();
	}

	/** Troca o que a fonte padrão não codifica (ex.: emoji, quebra de linha). */
	public static String seguro(PDType1Font fonte, String texto) {
		var limpo = new StringBuilder(texto.length());
		texto.codePoints().forEach(c -> {
			if (Character.isWhitespace(c)) {
				limpo.append(' ');
				return;
			}
			var caractere = new String(Character.toChars(c));
			try {
				fonte.encode(caractere);
				limpo.append(caractere);
			}
			catch (IOException | IllegalArgumentException erro) {
				limpo.append('?');
			}
		});
		return limpo.toString();
	}

	private static String cortar(PDType1Font fonte, float tamanho, String texto, float largura) throws IOException {
		if (fonte.getStringWidth(texto) / 1000 * tamanho <= largura) {
			return texto;
		}
		var corte = texto;
		while (!corte.isEmpty() && fonte.getStringWidth(corte + "…") / 1000 * tamanho > largura) {
			corte = corte.substring(0, corte.length() - 1);
		}
		return corte + "…";
	}

	private interface Acao {

		void executar() throws IOException;

	}

	private static void executar(Acao acao) {
		try {
			acao.executar();
		}
		catch (IOException erro) {
			throw new UncheckedIOException(erro);
		}
	}

}
