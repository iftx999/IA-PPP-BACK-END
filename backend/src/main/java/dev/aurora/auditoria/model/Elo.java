package dev.aurora.auditoria.model;

/** Último elo da cadeia de um tenant. */
public record Elo(long sequencia, byte[] hash) {

	public static final byte[] HASH_INICIAL = new byte[32];

}
