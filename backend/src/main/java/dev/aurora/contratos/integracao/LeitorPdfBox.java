package dev.aurora.contratos.integracao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.aurora.contratos.model.Pagina;
import dev.aurora.contratos.service.LeitorPdf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.stereotype.Component;

/** Texto por página com Apache PDFBox, na ordem de leitura. */
@Component
class LeitorPdfBox implements LeitorPdf {

	@Override
	public int contarPaginas(byte[] pdf) throws PdfInvalidoException {
		try (var documento = Loader.loadPDF(pdf)) {
			return documento.getNumberOfPages();
		}
		catch (InvalidPasswordException erro) {
			throw new PdfInvalidoException("o PDF está protegido por senha.", erro);
		}
		catch (IOException erro) {
			throw new PdfInvalidoException("o arquivo está corrompido ou não é um PDF.", erro);
		}
	}

	@Override
	public List<Pagina> lerPaginas(byte[] pdf) throws PdfInvalidoException {
		try (var documento = Loader.loadPDF(pdf)) {
			var leitor = new PDFTextStripper();
			leitor.setSortByPosition(true);
			var paginas = new ArrayList<Pagina>();
			for (int i = 1; i <= documento.getNumberOfPages(); i++) {
				leitor.setStartPage(i);
				leitor.setEndPage(i);
				paginas.add(new Pagina(i, leitor.getText(documento).strip()));
			}
			return paginas;
		}
		catch (InvalidPasswordException erro) {
			throw new PdfInvalidoException("o PDF está protegido por senha.", erro);
		}
		catch (IOException erro) {
			throw new PdfInvalidoException("o arquivo está corrompido ou não é um PDF.", erro);
		}
	}

}
