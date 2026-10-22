package dev.aurora.unidades.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tipos de unidade e a hierarquia permitida: Grupo > Concessão > Município > Setor
 * comercial | ETE | EEE. O grupo é a raiz do tenant e nasce no cadastro do tenant.
 */
public enum TipoUnidade {

	GRUPO("Grupo econômico"), CONCESSAO("Concessão / SPE"), MUNICIPIO("Município"), SETOR_COMERCIAL("Setor comercial"),
	ETE("Estação de tratamento de esgoto"), EEE("Estação elevatória de esgoto");

	private final String descricao;

	TipoUnidade(String descricao) {
		this.descricao = descricao;
	}

	public String descricao() {
		return descricao;
	}

	/** Tipos que podem ser pai deste; vazio para a raiz. */
	public Set<TipoUnidade> tiposPai() {
		return switch (this) {
			case GRUPO -> EnumSet.noneOf(TipoUnidade.class);
			case CONCESSAO -> EnumSet.of(GRUPO);
			case MUNICIPIO -> EnumSet.of(CONCESSAO);
			case SETOR_COMERCIAL, ETE, EEE -> EnumSet.of(MUNICIPIO);
		};
	}

	public boolean aceitaPai(TipoUnidade pai) {
		return tiposPai().contains(pai);
	}

	/** Município é identificado pelo código IBGE (7 dígitos); os demais não têm. */
	public boolean exigeCodigoIbge() {
		return this == MUNICIPIO;
	}

}
