package dev.aurora.assistente.model;

/** Tokens de uma pergunta (somados em todas as chamadas do laço de ferramentas). */
public record Uso(long entrada, long saida, long cacheLido, long cacheEscrito) {

	public static final Uso ZERO = new Uso(0, 0, 0, 0);

	public Uso mais(Uso outro) {
		return new Uso(entrada + outro.entrada, saida + outro.saida, cacheLido + outro.cacheLido,
				cacheEscrito + outro.cacheEscrito);
	}

}
