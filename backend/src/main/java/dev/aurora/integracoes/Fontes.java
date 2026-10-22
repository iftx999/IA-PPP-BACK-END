package dev.aurora.integracoes;

import java.util.List;

/** API do módulo de integrações para os demais módulos. */
public interface Fontes {

	/** Fontes ativas cuja última carga passou do atraso máximo (RN-08). */
	List<FonteAtrasada> atrasadas();

}
