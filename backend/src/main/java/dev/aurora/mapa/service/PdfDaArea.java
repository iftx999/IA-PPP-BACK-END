package dev.aurora.mapa.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.aurora.mapa.model.ImovelNoMapa;
import dev.aurora.mapa.model.Marcacao;
import dev.aurora.mapa.model.PedidoExportacao;
import dev.aurora.mapa.model.SituacaoNoMapa;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * PDF da área do mapa "para imprimir ou anexar a uma ordem de serviço" (E5): imagem do
 * mapa como a pessoa o via, resumo por situação e as listas de imóveis e marcações. A4
 * deitado, fontes padrão do PDF (texto fora do WinAnsi vira "?").
 */
class PdfDaArea {

	private static final PDRectangle PAGINA = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

	private static final float MARGEM = 36;

	private static final float LINHA = 13;

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",
			Locale.forLanguageTag("pt-BR"));

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
			Locale.forLanguageTag("pt-BR"));

	private final Clock relogio;

	private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private PDDocument documento;

	private PDPageContentStream conteudo;

	private float y;

	private int pagina;

	PdfDaArea(Clock relogio) {
		this.relogio = relogio;
	}

	void escrever(PedidoExportacao pedido, Map<SituacaoNoMapa, Long> porSituacao, List<ImovelNoMapa> imoveis,
			List<Marcacao> marcacoes, OutputStream saida) throws IOException {
		try (var doc = new PDDocument()) {
			documento = doc;
			var info = new PDDocumentInformation();
			info.setTitle("Aurora - Área do mapa");
			info.setProducer("Aurora");
			doc.setDocumentInformation(info);

			novaPagina();
			texto(negrito, 16, MARGEM, "Área do mapa");
			y -= 18;
			var gerado = ZonedDateTime.now(relogio);
			var subtitulo = new StringBuilder();
			if (pedido.imoveis() != null) {
				subtitulo.append("Dados de ").append(MES.format(pedido.imoveis().competencia())).append(" · ");
			}
			subtitulo.append("Emitido em ")
				.append(DATA_HORA.format(gerado))
				.append(" · Área ")
				.append(coordenadas(pedido));
			texto(normal, 9, MARGEM, subtitulo.toString());
			y -= 10;

			if (pedido.imagemPng() != null) {
				imagem(pedido.imagemPng());
			}

			var resumo = new StringBuilder();
			if (pedido.imoveis() != null) {
				resumo.append(imoveis.size()).append(imoveis.size() == 1 ? " imóvel" : " imóveis");
				porSituacao.forEach((situacao,
						quantidade) -> resumo.append(" · ").append(situacao.rotulo()).append(": ").append(quantidade));
			}
			if (pedido.marcacoes()) {
				resumo.append(resumo.isEmpty() ? "" : " · ")
					.append(marcacoes.size())
					.append(marcacoes.size() == 1 ? " marcação" : " marcações");
			}
			texto(negrito, 10, MARGEM, resumo.toString());
			y -= 6;

			if (pedido.imoveis() != null) {
				titulo("Imóveis");
				var colunas = new float[] { 110, 140, 90, 230, 80, 80 };
				cabecalho(colunas, "Matrícula", "Situação", "Localização", "Unidade", "Latitude", "Longitude");
				for (var i : imoveis) {
					linha(colunas, i.matricula(), i.situacao().rotulo(),
							i.precisao().aproximada() ? "Aproximada" : "Exata", i.unidadeNome(),
							coordenada(i.latitude()), coordenada(i.longitude()));
				}
			}
			if (pedido.marcacoes()) {
				titulo("Marcações");
				var colunas = new float[] { 150, 250, 120, 70, 90, 90 };
				cabecalho(colunas, "Título", "Observação", "Autor", "Data", "Latitude", "Longitude");
				for (var m : marcacoes) {
					linha(colunas, m.titulo(), m.observacao() == null ? "" : m.observacao(), m.autorNome(),
							DATA.format(m.criadaEm().atZone(relogio.getZone())), coordenada(m.latitude()),
							coordenada(m.longitude()));
				}
			}
			conteudo.close();
			doc.save(saida);
		}
	}

	private void imagem(byte[] png) throws IOException {
		var imagem = PDImageXObject.createFromByteArray(documento, png, "mapa.png");
		float largura = PAGINA.getWidth() - 2 * MARGEM;
		float altura = Math.min(330, largura * imagem.getHeight() / imagem.getWidth());
		float larguraFinal = altura * imagem.getWidth() / imagem.getHeight();
		y -= altura;
		conteudo.drawImage(imagem, MARGEM, y, larguraFinal, altura);
		y -= 14;
	}

	private void titulo(String texto) throws IOException {
		garantirEspaco(LINHA * 4);
		y -= 10;
		texto(negrito, 12, MARGEM, texto);
		y -= 16;
	}

	private void cabecalho(float[] colunas, String... celulas) throws IOException {
		garantirEspaco(LINHA * 2);
		escreverLinha(negrito, colunas, celulas);
		conteudo.setLineWidth(0.5f);
		conteudo.moveTo(MARGEM, y + LINHA - 3);
		conteudo.lineTo(PAGINA.getWidth() - MARGEM, y + LINHA - 3);
		conteudo.stroke();
	}

	private void linha(float[] colunas, String... celulas) throws IOException {
		if (garantirEspaco(LINHA)) {
			escreverLinha(negrito, colunas, "(continuação)");
		}
		escreverLinha(normal, colunas, celulas);
	}

	private void escreverLinha(PDType1Font fonte, float[] colunas, String... celulas) throws IOException {
		float x = MARGEM;
		for (int i = 0; i < celulas.length; i++) {
			texto(fonte, 8, x, cortar(fonte, 8, seguro(fonte, celulas[i]), colunas[i] - 6));
			x += colunas[i];
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
		desenharRodape();
		y -= 6;
	}

	private void desenharRodape() throws IOException {
		conteudo.setLineWidth(0.3f);
		conteudo.moveTo(MARGEM, y - 4);
		conteudo.lineTo(PAGINA.getWidth() - MARGEM, y - 4);
		conteudo.stroke();
		y -= 16;
	}

	private void texto(PDType1Font fonte, float tamanho, float x, String texto) throws IOException {
		conteudo.beginText();
		conteudo.setFont(fonte, tamanho);
		conteudo.newLineAtOffset(x, y);
		conteudo.showText(seguro(fonte, texto));
		conteudo.endText();
	}

	/** Troca o que a fonte padrão não codifica (ex.: emoji, quebra de linha). */
	static String seguro(PDType1Font fonte, String texto) {
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

	private static String coordenadas(PedidoExportacao pedido) {
		var a = pedido.area();
		return String.format(Locale.forLanguageTag("pt-BR"), "%.5f; %.5f a %.5f; %.5f", a.sul(), a.oeste(), a.norte(),
				a.leste());
	}

	private static String coordenada(double valor) {
		return String.format(Locale.ROOT, "%.6f", valor);
	}

}
