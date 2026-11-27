package dev.aurora.servicos;

import java.util.List;

/**
 * O que o app exige em cada fase da OS, numa versão do serviço (ADR 0053).
 *
 * @param foto fases com ao menos uma foto
 * @param observacao fases com observação escrita
 */
public record ExigenciasNasFases(List<FaseDaOrdem> foto, List<FaseDaOrdem> observacao) {

	/** Padrão: foto e observação em todas as fases. */
	public static final ExigenciasNasFases TODAS = new ExigenciasNasFases(FaseDaOrdem.TODAS, FaseDaOrdem.TODAS);

	public ExigenciasNasFases {
		foto = List.copyOf(foto);
		observacao = List.copyOf(observacao);
	}

}
