package dev.aurora.servicos.model;

/** Cadastro ou alteração de tipo de veículo. */
public record DadosTipoVeiculo(String codigo, String nome, PerfilRota perfilRota, boolean ativo) {

	public DadosTipoVeiculo normalizados() {
		return new DadosTipoVeiculo(Codigo.normalizar(codigo), Nomes.obrigatorio(nome, 2, 80, "Nome do veículo"),
				perfilRota, ativo);
	}

}
