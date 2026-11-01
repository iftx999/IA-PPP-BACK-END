package dev.aurora.auditoria.model;

/** Último elo da cadeia de um tenant. */
public record Elo(long sequencia, byte[] hash) {

	private static final int TAMANHO_DO_HASH = 32;

	/** Hash "anterior" do primeiro evento da cadeia (zeros); cópia nova a cada uso. */
	public static byte[] hashInicial() {
		return new byte[TAMANHO_DO_HASH];
	}

}
