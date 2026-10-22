package dev.aurora.integracoes.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Fonte de dados com o estado da última carga. */
public record FonteDados(UUID id, String nome, TipoAdaptador tipo, @Nullable String sistemaOrigem,
		@Nullable String frequencia, Duration atrasoMaximo, boolean ativo, Instant criadaEm,
		@Nullable Instant ultimaCarga, @Nullable String situacaoUltimaExecucao, @Nullable Instant ultimaExecucao,
		long credenciaisAtivas) {

	/**
	 * RN-08: ativa e sem carga concluída dentro do atraso máximo (conta da criação se
	 * nunca carregou).
	 */
	public boolean atrasadaEm(Instant agora) {
		var referencia = ultimaCarga != null ? ultimaCarga : criadaEm;
		return ativo && referencia.plus(atrasoMaximo).isBefore(agora);
	}

}
