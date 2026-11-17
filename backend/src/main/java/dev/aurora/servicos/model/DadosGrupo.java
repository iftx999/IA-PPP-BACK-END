package dev.aurora.servicos.model;

/** Cadastro ou alteração de grupo. */
public record DadosGrupo(String codigo, String nome, boolean ativo) {

	public DadosGrupo normalizados() {
		return new DadosGrupo(Codigo.normalizar(codigo), Nomes.obrigatorio(nome, 2, 80, "Nome do grupo"), ativo);
	}

}
