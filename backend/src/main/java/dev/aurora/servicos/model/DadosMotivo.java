package dev.aurora.servicos.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Cadastro ou alteração de motivo; "OUTRO" sempre exige observação. */
public record DadosMotivo(TipoMotivo tipo, String codigo, String nome, boolean exigeObservacao,
		@Nullable UUID servicoId, boolean ativo) {

	public DadosMotivo normalizados() {
		var codigoLimpo = Codigo.normalizar(codigo);
		return new DadosMotivo(tipo, codigoLimpo, Nomes.obrigatorio(nome, 2, 80, "Nome do motivo"),
				exigeObservacao || codigoLimpo.equals("OUTRO"), servicoId, ativo);
	}

}
