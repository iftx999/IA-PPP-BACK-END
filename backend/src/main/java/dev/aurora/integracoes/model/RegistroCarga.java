package dev.aurora.integracoes.model;

import org.jspecify.annotations.Nullable;

/**
 * Uma linha da carga como chegou: matrícula (código no sistema comercial), código da
 * unidade, situação e, para cortados, o motivo do corte (opcional: inadimplência ou a
 * pedido do cliente). Nunca nome nem endereço (RN-09).
 */
public record RegistroCarga(int linha, String matricula, String unidade, String situacao,
		@Nullable String motivoCorte) {

	public RegistroCarga(int linha, String matricula, String unidade, String situacao) {
		this(linha, matricula, unidade, situacao, null);
	}

}
