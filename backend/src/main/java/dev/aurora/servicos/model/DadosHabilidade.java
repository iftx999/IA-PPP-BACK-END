package dev.aurora.servicos.model;

/** Cadastro ou alteração de habilidade. */
public record DadosHabilidade(String codigo, String nome, boolean ativo) {

	public DadosHabilidade normalizados() {
		return new DadosHabilidade(Codigo.normalizar(codigo), Nomes.obrigatorio(nome, 2, 80, "Nome da habilidade"),
				ativo);
	}

}
