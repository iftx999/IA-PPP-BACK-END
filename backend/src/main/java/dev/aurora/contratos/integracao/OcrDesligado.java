package dev.aurora.contratos.integracao;

import dev.aurora.contratos.service.ReconhecedorDeTexto;

/** Sem Tesseract no ambiente: PDF digitalizado fica SEM_TEXTO, como antes do OCR. */
class OcrDesligado implements ReconhecedorDeTexto {

	@Override
	public boolean disponivel() {
		return false;
	}

	@Override
	public String reconhecer(byte[] png) throws ReconhecimentoFalhouException {
		throw new ReconhecimentoFalhouException("O reconhecimento de texto não está disponível.");
	}

}
