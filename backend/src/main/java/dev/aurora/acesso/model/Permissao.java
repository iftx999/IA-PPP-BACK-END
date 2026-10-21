package dev.aurora.acesso.model;

/** Item do catálogo global de permissões ("area:acao"). */
public record Permissao(String codigo, String descricao) {

	public static final String GERIR_USUARIOS = "usuario:gerenciar";

	/** Área para agrupar na matriz de permissões (parte antes de ":"). */
	public String area() {
		var separador = codigo.indexOf(':');
		return separador < 0 ? codigo : codigo.substring(0, separador);
	}

}
