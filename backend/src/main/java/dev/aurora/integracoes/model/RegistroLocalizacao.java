package dev.aurora.integracoes.model;

import org.jspecify.annotations.Nullable;

/**
 * Localização enviada pelo Agente Aurora (E6): só matrícula, unidade, ponto e precisão do
 * geocódigo; o endereço fica na rede da concessionária (RN-09).
 */
public record RegistroLocalizacao(int linha, @Nullable String matricula, @Nullable String unidade,
		@Nullable Double longitude, @Nullable Double latitude, @Nullable String precisao) {
}
